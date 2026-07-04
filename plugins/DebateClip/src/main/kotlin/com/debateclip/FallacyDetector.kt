package com.debateclip

import java.util.regex.Pattern

object FallacyDetector {

    private val patterns: List<Pair<String, Pattern>> = listOf(
        "Ad hominem" to Pattern.compile("\\b(idiot|stupid|moron|dumbass|dumb|clown|braindead|pathetic|you people|typical of (you|them))\\b", Pattern.CASE_INSENSITIVE),
        "Strawman" to Pattern.compile("\\bso (you're|you are|what you're) (saying|basically saying)|\\byou basically (want|think)\\b", Pattern.CASE_INSENSITIVE),
        "Whataboutism (tu quoque)" to Pattern.compile("\\b(but )?what about\\b|\\byou (do|did) it too\\b|\\byou're one to talk\\b|\\bhypocrite\\b", Pattern.CASE_INSENSITIVE),
        "Appeal to authority" to Pattern.compile("\\b(experts|scientists|doctors|studies) (say|show|agree|prove)\\b|\\bit'?s (a )?(well[- ]known|proven) fact\\b", Pattern.CASE_INSENSITIVE),
        "Appeal to popularity" to Pattern.compile("\\beveryone (knows|agrees|thinks)\\b|\\bmost people (agree|think|know)\\b|\\bnobody (believes|thinks) that\\b", Pattern.CASE_INSENSITIVE),
        "Slippery slope" to Pattern.compile("\\bnext thing (you know)?\\b|\\bbefore you know it\\b|\\bwhere does it end\\b|\\bslippery slope\\b|\\bit will (inevitably )?lead to\\b", Pattern.CASE_INSENSITIVE),
        "False dilemma" to Pattern.compile("\\byou'?re either\\b|\\beither .{1,40} or (else)?\\b|\\bonly two (options|choices)\\b|\\bthere'?s no middle ground\\b", Pattern.CASE_INSENSITIVE),
        "Hasty generalization" to Pattern.compile("\\ball (of (them|you)|men|women|people|\\w+s) (are|do|think)\\b|\\b(always|never) (does|do|works|happens)\\b", Pattern.CASE_INSENSITIVE),
        "Appeal to emotion" to Pattern.compile("\\bthink of the children\\b|\\bhow dare you\\b|\\byou should be ashamed\\b", Pattern.CASE_INSENSITIVE),
        "Appeal to ignorance" to Pattern.compile("\\bno one has (ever )?proven\\b|\\byou can'?t prove\\b|\\bthere'?s no evidence against\\b", Pattern.CASE_INSENSITIVE)
    )

    private data class Flag(val fallacy: String, val example: String)

    fun report(lines: List<Line>): String {
        val perAuthor = LinkedHashMap<String, MutableList<Flag>>()
        for (line in lines) {
            val flags = perAuthor.getOrPut(line.author) { mutableListOf() }
            for ((name, pattern) in patterns) {
                val matcher = pattern.matcher(line.content)
                if (matcher.find()) {
                    flags.add(Flag(name, matcher.group().trim()))
                }
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
