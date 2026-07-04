package com.debateclip

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.utils.RxUtils.await
import com.aliucord.utils.SnowflakeUtils
import com.discord.models.message.Message
import com.discord.models.user.CoreUser
import com.discord.utilities.rest.RestAPI
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.lytefast.flexinput.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line of the debate transcript. */
data class Line(val id: Long, val author: String, val content: String)

/** Current debate selection. Lives as long as the Discord process. */
object DebateState {
    var channelId = 0L
    var startId = 0L
    var endId = 0L

    /** userId -> username of the debaters to include. Supports any number of participants. */
    val participants = LinkedHashMap<Long, String>()

    val ready get() = channelId != 0L && startId != 0L && endId != 0L

    fun markStart(channel: Long, messageId: Long, user: CoreUser) {
        if (channel != channelId) reset()
        channelId = channel
        startId = messageId
        participants[user.id] = user.username
    }

    fun markEnd(channel: Long, messageId: Long, user: CoreUser) {
        if (channel != channelId) reset()
        channelId = channel
        endId = messageId
        participants[user.id] = user.username
    }

    fun toggle(user: CoreUser): Boolean =
        if (participants.containsKey(user.id)) {
            participants.remove(user.id); false
        } else {
            participants[user.id] = user.username; true
        }

    fun reset() {
        channelId = 0L; startId = 0L; endId = 0L
        participants.clear()
    }

    fun summary(): String = "${participants.size} debater(s)"
}

@AliucordPlugin
@Suppress("unused")
class DebateClip : Plugin() {
    private val log = Logger("DebateClip")

    init {
        settingsTab = SettingsTab(DebateClipSettings::class.java).withArgs(settings)
    }

    @SuppressLint("SetTextI18n")
    override fun start(context: Context) {
        patcher.patch(
            WidgetChatListActions::class.java.getDeclaredMethod(
                "configureUI",
                WidgetChatListActions.Model::class.java
            ),
            Hook { cf ->
                val widget = cf.thisObject as WidgetChatListActions
                val model = cf.args[0] as WidgetChatListActions.Model
                val msg = model.message ?: return@Hook
                val apiAuthor = msg.author ?: return@Hook
                val root = (widget.view as? NestedScrollView)
                    ?.getChildAt(0) as? LinearLayout ?: return@Hook
                val ctx = root.context
                val author = CoreUser(apiAuthor)

                fun addItem(label: String, onClick: () -> Unit) {
                    val tv = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon)
                    tv.text = label
                    tv.setOnClickListener {
                        widget.dismiss()
                        onClick()
                    }
                    root.addView(tv)
                }

                addItem("\uD83D\uDFE2 Debate: mark START here") {
                    DebateState.markStart(msg.channelId, msg.id, author)
                    Utils.showToast("Debate start set (${author.username} added)")
                }
                addItem("\uD83D\uDD34 Debate: mark END here") {
                    DebateState.markEnd(msg.channelId, msg.id, author)
                    Utils.showToast("Debate end set (${author.username} added)")
                }
                val inDebate = DebateState.participants.containsKey(author.id)
                addItem(
                    if (inDebate) "\u2796 Debate: remove ${author.username}"
                    else "\u2795 Debate: add ${author.username} as participant"
                ) {
                    val added = DebateState.toggle(author)
                    Utils.showToast(
                        if (added) "${author.username} added to debate"
                        else "${author.username} removed from debate"
                    )
                }
                if (DebateState.ready) {
                    addItem("\uD83D\uDCCB Debate: copy transcript (${DebateState.summary()})") { copyTranscript() }
                    addItem("\uD83E\uDDE0 Debate: analyze (fallacies \u2022 validity \u2022 facts)") { analyze() }
                    addItem("\u267B\uFE0F Debate: reset selection") {
                        DebateState.reset()
                        Utils.showToast("Debate selection cleared")
                    }
                }
            }
        )
    }

    override fun stop(context: Context) = patcher.unpatchAll()

    // ---------------------------------------------------------------- actions

    private fun copyTranscript() {
        Utils.showToast("DebateClip: fetching messages\u2026")
        Utils.threadPool.execute {
            try {
                val lines = collectLines()
                if (lines.isEmpty()) {
                    Utils.showToast("No messages from the selected participants in that range")
                    return@execute
                }
                val transcript = buildTranscript(lines)
                Utils.setClipboard("Debate transcript", transcript)
                Utils.showToast("Copied ${lines.size} messages to clipboard")
            } catch (t: Throwable) {
                log.error(t)
                Utils.showToast("DebateClip error: ${t.message}")
            }
        }
    }

    private fun analyze() {
        Utils.showToast("DebateClip: analyzing\u2026 this can take a minute")
        Utils.threadPool.execute {
            try {
                val lines = collectLines()
                if (lines.isEmpty()) {
                    Utils.showToast("No messages from the selected participants in that range")
                    return@execute
                }
                val transcript = buildTranscript(lines)
                val report = StringBuilder()

                // 1. Local heuristic fallacy counter (always available, no API needed)
                report.append(FallacyDetector.report(lines))

                // 2. LLM validity / soundness / fallacy analysis
                val key = settings.getString(S.API_KEY, "") ?: ""
                if (key.isBlank()) {
                    report.append(
                        "\n\n(No API key configured. Open Settings \u2192 Plugins \u2192 DebateClip " +
                            "to unlock the full validity/soundness analysis and fact-checking.)"
                    )
                } else {
                    val raw = LlmClient.chat(settings, Prompts.ANALYSIS_SYSTEM, Prompts.analysisPrompt(transcript))
                    val (analysis, claims) = Prompts.splitClaims(raw)
                    report.append("\n\n\u2550\u2550 Logic analysis (LLM) \u2550\u2550\n").append(analysis.trim())

                    // 3. Optional web fact-check of extracted claims
                    if (settings.getBool(S.FACT_CHECK, true) && claims.isNotEmpty()) {
                        val evidence = FactChecker.gatherEvidence(claims)
                        val verdicts = LlmClient.chat(settings, Prompts.FACT_SYSTEM, Prompts.factPrompt(claims, evidence))
                        report.append("\n\n\u2550\u2550 Fact check \u2550\u2550\n").append(verdicts.trim())
                    }
                }

                showTextDialog("Debate analysis", report.toString())
            } catch (t: Throwable) {
                log.error(t)
                Utils.showToast("DebateClip error: ${t.message}")
            }
        }
    }

    // ---------------------------------------------------------- data fetching

    /** Fetch every message in the marked range and keep only the chosen participants. */
    private fun collectLines(): List<Line> {
        var start = DebateState.startId
        var end = DebateState.endId
        if (start > end) { val t = start; start = end; end = t }

        val maxMessages = (settings.getString(S.MAX_MSGS, "500") ?: "500").toIntOrNull() ?: 500
        val ids = DebateState.participants.keys.toSet()

        val out = ArrayList<Line>()
        var after = start - 1
        while (out.size < maxMessages) {
            val (batch, err) = RestAPI.api
                .getChannelMessages(DebateState.channelId, null, after, 100)
                .await()
            if (err != null) throw RuntimeException("Failed to fetch messages: ${err.message}")
            if (batch == null || batch.isEmpty()) break

            val models = batch.map { Message(it) }.sortedBy { it.id }
            for (m in models) {
                if (m.id !in start..end) continue
                val a = m.author ?: continue
                val user = CoreUser(a)
                if (user.id !in ids) continue
                val content = m.content?.takeIf { it.isNotBlank() } ?: "[non-text message]"
                out.add(Line(m.id, user.username, content))
            }
            after = models.last().id
            if (after >= end || models.size < 100) break
        }
        return out
    }

    private fun buildTranscript(lines: List<Line>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        fun ts(id: Long) = fmt.format(Date(SnowflakeUtils.toTimestamp(id)))

        val header = buildString {
            append("Debate transcript\n")
            append("Participants: ${DebateState.participants.values.joinToString(", ")}\n")
            append("Range: ${ts(lines.first().id)} \u2192 ${ts(lines.last().id)} (${lines.size} messages)\n")
            append("\u2500".repeat(40)).append('\n')
        }
        return header + lines.joinToString("\n") { "[${ts(it.id)}] ${it.author}: ${it.content}" }
    }

    // ------------------------------------------------------------------- UI

    private fun showTextDialog(title: String, body: String) {
        Utils.mainThread.post {
            val act = Utils.appActivity
            val tv = TextView(act).apply {
                text = body
                setTextIsSelectable(true)
                setPadding(48, 24, 48, 24)
            }
            val scroll = ScrollView(act).apply { addView(tv) }
            AlertDialog.Builder(act)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Copy") { _, _ ->
                    Utils.setClipboard(title, body)
                    Utils.showToast("Analysis copied")
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }
}

