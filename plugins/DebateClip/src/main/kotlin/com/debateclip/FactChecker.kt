package com.debateclip

import com.aliucord.Http
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Gathers free, key-less evidence for factual claims:
 *  - Wikipedia search snippets (en.wikipedia.org API)
 *  - DuckDuckGo Instant Answer abstracts
 * The snippets are then handed to the LLM for verdicts.
 */
object FactChecker {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), "").trim()

    private fun get(url: String): String =
        Http.Request(url)
            .setHeader("User-Agent", "DebateClip-AliucordPlugin/1.0")
            .setRequestTimeout(15_000)
            .execute()
            .text()

    private fun wikipedia(claim: String): List<String> = try {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=2&srsearch=" + enc(claim)
        val arr = JSONObject(get(url)).getJSONObject("query").getJSONArray("search")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            "[Wikipedia: ${o.getString("title")}] " + stripHtml(o.optString("snippet"))
        }
    } catch (t: Throwable) {
        emptyList()
    }

    private fun duckDuckGo(claim: String): List<String> = try {
        val url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" + enc(claim)
        val json = JSONObject(get(url))
        val abs = json.optString("AbstractText")
        if (abs.isNullOrBlank()) emptyList()
        else listOf("[DuckDuckGo: ${json.optString("AbstractSource", "web")}] $abs")
    } catch (t: Throwable) {
        emptyList()
    }

    fun gatherEvidence(claims: List<String>): String {
        val sb = StringBuilder()
        for ((i, claim) in claims.withIndex()) {
            sb.append("--- Evidence for claim ${i + 1}: \u201C$claim\u201D ---\n")
            val snippets = wikipedia(claim) + duckDuckGo(claim)
            if (snippets.isEmpty()) sb.append("(no evidence found)\n")
            else snippets.forEach { sb.append(it).append('\n') }
        }
        return sb.toString()
    }
}

