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
    private val HIGH = Regex("(?<=[\\p{L}\\p{N}»…)\\]*])[ \\u00A0\\u202F]?([!?;]+)(?=\\s|$|[»\"'’)\\]*_~])")
    private val COLON = Regex("(?<=[\\p{L}\\p{N}»)])[ \\u00A0\\u202F]?:(?=\\s|$)")

    fun french(src: String): String {
        var s = src.replace(". . .", "…").replace("...", "…")
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
