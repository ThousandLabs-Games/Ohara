package com.thousandlabs.ohara.translate

/**
 * Typographie française sur le texte traduit : guillemets « », points de suspension,
 * espaces insécables avant ! ? ; : — comme dans un livre.
 */
object Typo {
    private const val NB = "\u00A0"     // espace insécable
    private const val NNB = "\u202F"    // espace fine insécable

    private val OPEN_SP = Regex("«[ \\u00A0\\u202F]*")
    private val CLOSE_SP = Regex("[ \\u00A0\\u202F]*»")
    private val HIGH = Regex("(?<=[\\p{L}\\p{N}»…)\\]*\u27e7\u27eb])[ \\u00A0\\u202F]?([!?;]+)(?=\\s|$|[»\"'’)\\]*_~\u27e7\u27eb])")
    private val COLON = Regex("(?<=[\\p{L}\\p{N}»)\u27e7\u27eb])[ \\u00A0\\u202F]?:(?=\\s|$)")

    private val ENT = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|quot|amp|lt|gt|apos|nbsp);")

    /** Le service en ligne rend parfois « &#39; » ou « &quot; » au lieu de l'apostrophe ou du guillemet. */
    fun unescape(s: String): String {
        if ('&' !in s) return s
        return ENT.replace(s) { m ->
            val g = m.groupValues[1]
            when {
                g == "quot" -> "\""
                g == "amp" -> "&"
                g == "lt" -> "<"
                g == "gt" -> ">"
                g == "apos" -> "'"
                g == "nbsp" -> " "
                g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                else -> g.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
    }

    private val MULTI_SPACE = Regex("[ \\t]{2,}")
    private val SPACE_BEFORE_PUNCT = Regex(" +([,.])(?=\\s|$)")

    fun french(src: String): String {
        var s = unescape(src).replace(". . .", "…").replace("...", "…")
        s = MULTI_SPACE.replace(s, " ")
        s = SPACE_BEFORE_PUNCT.replace(s) { it.groupValues[1] }
        s = s.replace('“', '«').replace('”', '»')
        // Guillemets droits : seulement s'ils vont par paires (un dialogue qui continue au paragraphe suivant n'en a qu'un).
        val n = s.count { it == '"' }
        if (n > 0 && n % 2 == 0) {
            var open = true
            s = buildString { for (c in s) if (c == '"') { append(if (open) '«' else '»'); open = !open } else append(c) }
        }
        if ('«' in s) s = OPEN_SP.replace(s, "«$NB")
        if ('»' in s) s = CLOSE_SP.replace(s, "$NB»")
        s = HIGH.replace(s) { NNB + it.groupValues[1] }
        s = COLON.replace(s, "$NB:")
        // « « » vide en début ou fin de texte après remplacement : on enlève l'espace pendante.
        return s.replace(Regex("«$NB$"), "«").replace(Regex("^$NB»"), "»")
    }
}
