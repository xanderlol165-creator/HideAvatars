package com.debateclip

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.TextInput
import com.discord.views.CheckedSetting
import com.lytefast.flexinput.R

class DebateClipSettings(private val settings: SettingsAPI) : SettingsPage() {

    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("DebateClip")
        val ctx = view.context

        addView(header(ctx, "LLM analysis (optional but recommended)"))
        addView(
            input(ctx, "API base URL", S.BASE_URL, "https://api.openai.com/v1")
        )
        addView(
            input(ctx, "API key", S.API_KEY, "", password = true)
        )
        addView(
            input(ctx, "Model", S.MODEL, "gpt-4o-mini")
        )

        addView(
            Utils.createCheckedSetting(
                ctx, CheckedSetting.ViewType.SWITCH,
                "Web fact-check",
                "Gather Wikipedia/DuckDuckGo evidence for factual claims and ask the model for verdicts"
            ).apply {
                isChecked = settings.getBool(S.FACT_CHECK, true)
                setOnCheckedListener { settings.setBool(S.FACT_CHECK, it) }
            }
        )

        addView(header(ctx, "Fetching"))
        addView(
            input(ctx, "Max messages to fetch per debate", S.MAX_MSGS, "500", number = true)
        )

        addView(TextView(ctx).apply {
            setPadding(24, 24, 24, 24)
            text = "Works with any OpenAI-compatible endpoint (OpenAI, OpenRouter, Groq, " +
                "local Ollama, \u2026) or the Anthropic API (base URL containing \u201Canthropic\u201D). " +
                "Without a key you still get the transcript copier and the offline fallacy counter."
        })
    }

    private fun header(ctx: Context, text: String): TextView =
        TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply { this.text = text }

    private fun input(
        ctx: Context,
        hint: String,
        key: String,
        default: String,
        password: Boolean = false,
        number: Boolean = false
    ): TextInput = TextInput(ctx, hint).apply {
        editText.setText(settings.getString(key, default))
        if (password) editText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        if (number) editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                settings.setString(key, s.toString())
            }
        })
    }
}

