package app.wishtube.recommend

object LanguageDetector {
    private val englishStopwords = setOf(
        "the", "and", "to", "a", "of", "in", "is", "it", "you", "that", "he", "was", "for", "on", "are",
        "with", "as", "i", "his", "they", "be", "at", "one", "have", "this", "from", "or", "had", "by",
        "not", "word", "but", "what", "some", "we", "can", "out", "other", "were", "all", "your", "when",
        "use", "how", "said", "an", "each", "she", "which", "do", "their", "time", "if", "will", "way"
    )

    fun detectLanguage(text: String): String {
        val words = text.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (words.isEmpty()) return "en"
        val englishMatches = words.count { it in englishStopwords }
        return if (englishMatches > 0 || words.size < 5) "en" else "und"
    }

    fun isPreferred(title: String, description: String, preferred: String = "en"): Boolean {
        if (preferred.isBlank() || preferred == "any") return true
        val combined = "$title $description"
        val detected = detectLanguage(combined)
        return detected == preferred || detected == "und" || detected.startsWith(preferred)
    }
}
