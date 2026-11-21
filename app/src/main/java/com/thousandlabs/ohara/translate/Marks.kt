package com.thousandlabs.ohara.translate

/**
 * Repères d'italique et de gras dans un texte à traduire.
 * La page envoie « Il a dit ⟦non⟧ à voix haute » ; ⟦ ⟧ entourent l'italique, ⟪ ⟫ le gras.
 * Après la traduction, on vérifie que les repères sont revenus bien appariés ; sinon on les enlève
 * (le texte reste juste, seule la mise en forme interne à la phrase est perdue).
 */
object Marks {
    const val IO = '⟦'
    const val IC = '⟧'
    const val BO = '⟪'
    const val BC = '⟫'

    private fun isMark(c: Char) = c == IO || c == IC || c == BO || c == BC

    fun has(s: String): Boolean = s.any { isMark(it) }

    fun strip(s: String): String = if (has(s)) s.filterNot { isMark(it) } else s

    private fun count(s: String, c: Char) = s.count { it == c }

    /** Les repères sont-ils tous fermés, sans imbrication ? */
    internal fun balanced(s: String): Boolean {
        var open = '\u0000'
        for (c in s) {
            when (c) {
                IO, BO -> { if (open != '\u0000') return false; open = c }
                IC -> { if (open != IO) return false; open = '\u0000' }
                BC -> { if (open != BO) return false; open = '\u0000' }
            }
        }
        return open == '\u0000'
    }

    private val SPACE_AFTER_OPEN = Regex("([⟦⟪])(\\s+)")
    private val SPACE_BEFORE_CLOSE = Regex("(\\s+)([⟧⟫])")

    /** Traduction d'un texte avec repères : repères gardés s'ils sont cohérents, sinon enlevés. */
    fun fix(src: String, tr: String): String {
        if (!has(tr)) return tr
        if (!has(src)) return strip(tr)
        // les espaces collés à l'intérieur d'un repère passent à l'extérieur
        val t = SPACE_BEFORE_CLOSE.replace(SPACE_AFTER_OPEN.replace(tr) { it.groupValues[2] + it.groupValues[1] }) { it.groupValues[2] + it.groupValues[1] }
        val ok = balanced(t) && count(t, IO) <= count(src, IO) && count(t, BO) <= count(src, BO)
        return if (ok) t else strip(t)
    }
}
