package com.eventmanager.app.data.remote

/**
 * Short display labels for Firebase org IDs in compact UI (POS rail, clock row, etc.).
 */
object FirebaseOrgAbbreviation {
    private val stopWords = setOf(
        "et", "ou", "and", "or", "de", "du", "des", "la", "le", "les", "the", "a", "an", "of",
    )

    fun abbreviate(orgId: String, maxLength: Int = 3): String {
        val trimmed = orgId.trim()
        if (trimmed.isEmpty()) return ""

        val tokens = trimmed
            .split(Regex("[\\s\\-_]+"))
            .filter { it.isNotBlank() }
        val significant = tokens.filter { it.lowercase() !in stopWords }
        val words = when {
            significant.size >= 2 -> significant
            tokens.size >= 2 -> tokens
            else -> tokens
        }

        if (words.size == 2) {
            val letterWord = words.firstOrNull { part -> part.any(Char::isLetter) }
            val digitWord = words.firstOrNull { part -> part.all(Char::isDigit) }
            if (letterWord != null && digitWord != null) {
                return (letterWord.first().toString() + digitWord).uppercase().take(maxLength)
            }
        }

        if (words.size in 2..3) {
            return words
                .take(3)
                .map { it.first().uppercaseChar() }
                .joinToString("")
                .take(maxLength)
        }

        val single = words.singleOrNull() ?: trimmed.replace(" ", "")
        val letterThenDigits = Regex("^([A-Za-z])([A-Za-z]*?)(\\d+)$")
        letterThenDigits.matchEntire(single)?.let { match ->
            return (match.groupValues[1] + match.groupValues[3]).uppercase().take(maxLength)
        }

        val camelParts = Regex("([A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)|\\d+)")
            .findAll(single)
            .map { it.value }
            .toList()
        if (camelParts.size >= 2) {
            val letters = camelParts.filter { part -> part.any(Char::isLetter) }
            val numbers = camelParts.filter { part -> part.all(Char::isDigit) }
            if (letters.size == 1 && numbers.size == 1) {
                return (letters[0].first().toString() + numbers[0]).uppercase().take(maxLength)
            }
            if (letters.size >= 2) {
                return letters
                    .take(3)
                    .map { it.first().uppercaseChar() }
                    .joinToString("")
                    .take(maxLength)
            }
        }

        return single.take(maxLength).uppercase()
    }
}
