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
    val participants = java.util.LinkedHashMap<Long, String>()

    val ready get() = channelId != 0L && startId != 0L && endId != 0L

    fun markStart(channel: Long, messageId: Long, user: CoreUser) {
        if (channel != channelId) reset()
        channelId = channel
        startId = messageId
        participants.put(user.id, user.username)
    }

    fun markEnd(channel: Long, messageId: Long, user: CoreUser) {
        if (channel != channelId) reset()
        channelId = channel
        endId = messageId
        participants.put(user.id, user.username)
    }

    fun toggle(user: CoreUser): Boolean =
        if (participants.containsKey(user.id)) {
            participants.remove(user.id); false
        } else {
            participants.put(user.id, user.username); true
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

    private fun copyTranscript() {
        Utils.showToast("DebateClip: fetching messages\u2026")
        Utils.threadPool.execute {
            try {
                val lines = collectLines()
                if (lines.size == 0) {
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
                if (lines.size == 0) {
                    Utils.showToast("No messages from the selected participants in that range")
                    return@execute
                }
                val transcript = buildTranscript(lines)
                val report = java.lang.StringBuilder()

                report.append(FallacyDetector.report(lines))

                // Manually extracting settings via literal strings to prevent missing reference crashes
                val key = settings.getString("api_key", "") ?: ""
                if (key.trim().isEmpty()) {
                    report.append(
                        "\n\n(No API key configured. Open Settings \u2192 Plugins \u2192 DebateClip " +
                            "to unlock the full validity/soundness analysis and fact-checking.)"
                    )
                } else {
                    val raw = LlmClient.chat(settings, Prompts.ANALYSIS_SYSTEM, Prompts.analysisPrompt(transcript))
                    
                    // Replaced Kotlin destructuring to avoid Component1 iterators
                    val split = Prompts.splitClaims(raw)
                    val analysis = split.first
                    val claims = split.second
                    
                    report.append("\n\n\u2550\u2550 Logic analysis (LLM) \u2550\u2550\n").append(analysis.trim())

                    if (settings.getBool("fact_check", true) && claims.isNotEmpty()) {
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

    private fun collectLines(): List<Line> {
        var start = DebateState.startId
        var end = DebateState.endId
        if (start > end) { val t = start; start = end; end = t }

        var maxMessages = 500
        try {
            val maxStr = settings.getString("max_msgs", "500")
            if (maxStr != null) maxMessages = java.lang.Integer.parseInt(maxStr)
        } catch (ignored: Exception) {}

        val out = ArrayList<Line>()
        var after = start - 1

        while (out.size < maxMessages) {
            val raw = try {
                RestAPI.api.getChannelMessages(DebateState.channelId, null, after, 100).await()
            } catch (e: Exception) {
                throw RuntimeException("Failed to fetch messages: " + e.message)
            }

            if (raw !is Pair<*, *>) {
                throw RuntimeException("Unexpected getChannelMessages result type")
            }
            
            val result = raw.first
            val err = raw.second
            
            if (err != null) {
                throw RuntimeException("getChannelMessages failed: " + err.toString())
            }
            
            val batch = result as? java.util.List<*> ?: break

            val models = ArrayList<Message>()
            
            var i = 0
            val size = batch.size
            while (i < size) {
                val item = batch.get(i)
                i++
                if (item == null) continue
                
                when (item) {
                    is Message -> models.add(item)
                    is com.discord.api.message.Message -> {
                        try {
                            models.add(Message(item))
                        } catch (e2: Exception) {
                            log.error("DebateClip: Failed to wrap message", e2)
                        }
                    }
                    else -> log.error("DebateClip: unrecognized message type", java.lang.RuntimeException(item.javaClass.name))
                }
            }

            java.util.Collections.sort(models, java.util.Comparator { m1, m2 -> m1.id.compareTo(m2.id) })

            var j = 0
            val modelsSize = models.size
            while (j < modelsSize) {
                val m = models.get(j)
                j++
                if (m.id < start || m.id > end) continue
                val a = m.author ?: continue
                val user = CoreUser(a)
                
                // CRITICAL: Bypassed Kotlin .toSet() entirely. Direct Java Map lookup.
                if (!DebateState.participants.containsKey(user.id)) continue
                
                // CRITICAL: Bypassed Kotlin .takeIf and .isNotBlank
                var contentStr = m.content
                if (contentStr == null || contentStr.trim().isEmpty()) {
                    contentStr = "[non-text message]"
                }
                out.add(Line(m.id, user.username, contentStr))
            }

            if (models.isEmpty()) break
            
            // CRITICAL: Bypassed Kotlin .last()
            after = models.get(models.size - 1).id
            if (after >= end || models.size < 100) break
        }
        return out
    }

    private fun buildTranscript(lines: List<Line>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val sb = java.lang.StringBuilder()
        sb.append("Debate transcript\n")
        sb.append("Participants: ")
        
        // CRITICAL: Bypassed joinToString. Using primitive Java iterator to prevent R8 crashes.
        val pIter = DebateState.participants.values.iterator()
        var pCount = 0
        val pSize = DebateState.participants.size
        while (pIter.hasNext()) {
            sb.append(pIter.next())
            pCount++
            if (pCount < pSize) sb.append(", ")
        }
        
        sb.append("\nRange: ")
        // CRITICAL: Bypassed lines.first() and lines.last()
        sb.append(fmt.format(Date((lines.get(0).id ushr 22) + 1420070400000L)))
        sb.append(" \u2192 ")
        sb.append(fmt.format(Date((lines.get(lines.size - 1).id ushr 22) + 1420070400000L)))
        sb.append(" (")
        sb.append(lines.size)
        sb.append(" messages)\n")
        
        // CRITICAL: Bypassed .repeat(40) to kill the final hidden IntIterator
        sb.append("────────────────────────────────────────\n")
        
        var k = 0
        val linesSize = lines.size
        while (k < linesSize) {
            val it = lines.get(k)
            k++
            sb.append("[")
            sb.append(fmt.format(Date((it.id ushr 22) + 1420070400000L)))
            sb.append("] ")
            sb.append(it.author)
            sb.append(": ")
            sb.append(it.content)
            if (k < linesSize) sb.append("\n")
        }
        return sb.toString()
    }

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
