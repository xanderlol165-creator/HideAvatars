package com.debateclip

/**
 * Fast, offline, keyword-based fallacy flagger.
 * These are heuristics: they flag *candidate* fallacies by surface patterns.
 * The LLM analysis is the authoritative judgment; this counter works with no API key.
 */
object FallacyDetector {

    private val patterns: List<Pair<String, Regex>> = listOf(
        "Ad hominem" to Regex(
            "\\b(idiot|stupid|moron|dumbass|dumb|clown|braindead|pathetic|you people|typical of (you|them))\\b",
            RegexOption.IGNORE_CASE
        ),
        "Strawman" to Regex(
            "\\bso (you're|you are|what you're) (saying|basically saying)|\\byou basically (want|think)\\b",
            RegexOption.IGNORE_CASE
        ),
        "Whataboutism (tu quoque)" to Regex(
            "\\b(but )?what about\\b|\\byou (do|did) it too\\b|\\byou're one to talk\\b|\\bhypocrite\\b",
            RegexOption.IGNORE_CASE
        ),
        "Appeal to authority" to Regex(
            "\\b(experts|scientists|doctors|studies) (say|show|agree|prove)\\b|\\bit'?s (a )?(well[- ]known|proven) fact\\b",
            RegexOption.IGNORE_CASE
        ),
        "Appeal to popularity" to Regex(
            "\\beveryone (knows|agrees|thinks)\\b|\\bmost people (agree|think|know)\\b|\\bnobody (believes|thinks) that\\b",
            RegexOption.IGNORE_CASE
        ),
        "Slippery slope" to Regex(
            "\\bnext thing (you know)?\\b|\\bbefore you know it\\b|\\bwhere does it end\\b|\\bslippery slope\\b|\\bit will (inevitably )?lead to\\b",
            RegexOption.IGNORE_CASE
        ),
        "False dilemma" to Regex(
            "\\byou'?re either\\b|\\beither .{1,40} or (else)?\\b|\\bonly two (options|choices)\\b|\\bthere'?s no middle ground\\b",
            RegexOption.IGNORE_CASE
        ),
        "Hasty generalization" to Regex(
            "\\ball (of (them|you)|men|women|people|\\w+s) (are|do|think)\\b|\\b(always|never) (does|do|works|happens)\\b",
            RegexOption.IGNORE_CASE
        ),
        "Appeal to emotion" to Regex(
            "\\bthink of the children\\b|\\bhow dare you\\b|\\byou should be ashamed\\b",
            RegexOption.IGNORE_CASE
        ),
        "Appeal to ignorance" to Regex(
            "\\bno one has (ever )?proven\\b|\\byou can'?t prove\\b|\\bthere'?s no evidence against\\b",
            RegexOption.IGNORE_CASE
        )
    )

    private data class Flag(val fallacy: String, val example: String)

    fun report(lines: List<Line>): String {
        val perAuthor = LinkedHashMap<String, MutableList<Flag>>()
        for (line in lines) {
            val flags = perAuthor.getOrPut(line.author) { mutableListOf() }
            for ((name, rx) in patterns) {
                val match = rx.find(line.content) ?: continue
                flags.add(Flag(name, match.value.trim()))
            }
        }

        val sb = StringBuilder("\u2550\u2550 Heuristic fallacy counter (keyword-based, indicative only) \u2550\u2550\n")
        for ((author, flags) in perAuthor) {
            if (flags.isEmpty()) {
                sb.append("$author: clean (0 flags)\n")
                continue
            }
            sb.append("$author: ${flags.size} flag(s)\n")
            flags.groupBy { it.fallacy }.forEach { (fallacy, fs) ->
                sb.append("  \u2022 $fallacy \u00D7${fs.size} (e.g. \u201C${fs.first().example}\u201D)\n")
            }
        }
        return sb.toString().trimEnd()
    }
}

