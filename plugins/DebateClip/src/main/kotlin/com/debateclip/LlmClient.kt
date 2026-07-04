package com.debateclip

import com.aliucord.Http
import com.aliucord.api.SettingsAPI
import org.json.JSONArray
import org.json.JSONObject

/** Settings keys. */
object S {
    const val BASE_URL = "apiBaseUrl"
    const val API_KEY = "apiKey"
    const val MODEL = "model"
    const val FACT_CHECK = "factCheck"
    const val MAX_MSGS = "maxMessages"
}

/**
 * Minimal LLM client. Talks to any OpenAI-compatible /chat/completions endpoint
 * (OpenAI, OpenRouter, Groq, local llama.cpp / Ollama with an exposed URL, etc.)
 * or to the Anthropic Messages API if the base URL contains "anthropic".
 */
object LlmClient {

    fun chat(settings: SettingsAPI, system: String, user: String): String {
        val baseUrl = (settings.getString(S.BASE_URL, "https://api.openai.com/v1") ?: "").trimEnd('/')
        val key = settings.getString(S.API_KEY, "") ?: ""
        val model = settings.getString(S.MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        require(key.isNotBlank()) { "Set an API key in DebateClip settings first" }

        return if (baseUrl.contains("anthropic")) anthropic(baseUrl, key, model, system, user)
        else openAi(baseUrl, key, model, system, user)
    }

    private fun openAi(baseUrl: String, key: String, model: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .put("temperature", 0.2)

        val res = Http.Request("$baseUrl/chat/completions", "POST")
            .setHeader("Content-Type", "application/json")
            .setHeader("Authorization", "Bearer $key")
            .setRequestTimeout(120_000)
            .executeWithBody(body.toString())

        val json = JSONObject(res.text())
        if (json.has("error")) throw RuntimeException("API error: " + json.getJSONObject("error").optString("message"))
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun anthropic(baseUrl: String, key: String, model: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4000)
            .put("system", system)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "user").put("content", user))
            )

        val res = Http.Request("$baseUrl/messages", "POST")
            .setHeader("Content-Type", "application/json")
            .setHeader("x-api-key", key)
            .setHeader("anthropic-version", "2023-06-01")
            .setRequestTimeout(120_000)
            .executeWithBody(body.toString())

        val json = JSONObject(res.text())
        if (json.has("error")) throw RuntimeException("API error: " + json.getJSONObject("error").optString("message"))
        return json.getJSONArray("content").getJSONObject(0).getString("text")
    }
}

/** Prompt templates and response parsing. */
object Prompts {

    val ANALYSIS_SYSTEM = """
        You are an expert in informal logic, argumentation theory, and analytic philosophy.
        You referee debates with precision and strict neutrality. Only flag a fallacy when
        the quoted text genuinely commits it; do not pad the list. Distinguish carefully
        between validity (does the conclusion follow from the premises?) and soundness
        (are the premises actually true?).
    """.trimIndent()

    fun analysisPrompt(transcript: String) = """
        Below is a debate transcript. Analyze it.

        For EACH participant:
        1. Reconstruct their main argument(s) as numbered premises and a conclusion.
        2. Assess deductive VALIDITY where applicable, and SOUNDNESS (premise truth).
           For inductive arguments, assess inductive strength instead.
        3. List every logical fallacy committed: fallacy name, the exact quote, and a
           one-sentence explanation of why it qualifies.

        Then provide:
        4. A fallacy count table per participant.
        5. An overall verdict: who argued more validly and soundly, and why.

        Finally, on its own line, output exactly:
        CLAIMS_JSON: ["claim 1", "claim 2", ...]
        containing up to 6 empirically checkable factual claims made in the debate
        (verbatim or minimally paraphrased), or CLAIMS_JSON: [] if there are none.

        TRANSCRIPT:
        $transcript
    """.trimIndent()

    val FACT_SYSTEM = """
        You are a careful fact-checker. Use ONLY the provided evidence snippets plus
        well-established general knowledge. Be honest about uncertainty; never invent
        sources. Verdicts: SUPPORTED, CONTRADICTED, MIXED, or UNVERIFIABLE.
    """.trimIndent()

    fun factPrompt(claims: List<String>, evidence: String) = """
        Fact-check the following claims from a debate.

        CLAIMS:
        ${claims.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n")}

        EVIDENCE GATHERED FROM THE WEB:
        $evidence

        For each claim output:
        N. <claim> — VERDICT — one-line rationale, citing which evidence snippet (if any) supports the verdict.
    """.trimIndent()

    /** Split the analysis text from the CLAIMS_JSON line the model was asked to emit. */
    fun splitClaims(raw: String): Pair<String, List<String>> {
        val idx = raw.lastIndexOf("CLAIMS_JSON:")
        if (idx == -1) return raw to emptyList()
        val analysis = raw.substring(0, idx)
        val claims = try {
            val tail = raw.substring(idx + "CLAIMS_JSON:".length).trim()
            val start = tail.indexOf('[')
            val end = tail.lastIndexOf(']')
            if (start == -1 || end <= start) emptyList()
            else {
                val arr = JSONArray(tail.substring(start, end + 1))
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            emptyList()
        }
        return analysis to claims
    }
}

