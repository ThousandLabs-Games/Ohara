package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic

/**
 * Les types de fics recherchés (self-insert, crossover, AU…). Les sites ne les indiquent pas toujours :
 * Ohara les repère dans le titre, le résumé, les étiquettes et les genres.
 */
enum class FicType(val code: String, val label: String, val sbTag: String?) {
    SELF_INSERT("si", "Self-insert", "self insert"),
    CROSSOVER("xover", "Crossover", "crossover"),
    AU("au", "AU", "alternate universe"),
    FIX_IT("fixit", "Fix-it", "fix-it"),
    ISEKAI("isekai", "Isekai", "isekai"),
    TIME_TRAVEL("time", "Time travel", "time travel"),
    SYSTEM("system", "System", "gamer"),
    TRANSMIGRATION("transmig", "Transmigration", "transmigration"),
    OC("oc", "OC", "original character");

    companion object {
        fun of(code: String): FicType? = entries.firstOrNull { it.code == code }
    }
}

object Types {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val PATTERNS: Map<FicType, Regex> = mapOf(
        FicType.SELF_INSERT to rx("\\b(self[- ]?insert(ed|s)?|s\\.i\\.|si[- ]?fic|oc[- ]?insert|\\bsi\\b|insert fic)"),
        FicType.CROSSOVER to rx("\\b(crossover|cross[- ]over|x[- ]?over)\\b"),
        FicType.AU to rx("\\b(au|a\\.u\\.|alternate universe|alternative universe|alt[- ]universe|alternate reality|what if)\\b"),
        FicType.FIX_IT to rx("\\b(fix[- ]?it|fix[- ]?fic|everyone lives|nobody dies|canon divergence|fixing (it|everything|canon))\\b"),
        FicType.ISEKAI to rx("\\b(isekai|reincarnat\\w*|reborn (in|as|into)|another world|transported to|summoned to|new world)\\b"),
        FicType.TIME_TRAVEL to rx("\\b(time[- ]?travel\\w*|back in time|sent back|second chance|do[- ]?over|redo|groundhog|regress\\w*|time loop|loop fic)\\b"),
        FicType.SYSTEM to rx("\\b(system|gamer|game mechanics|litrpg|lit rpg|status window|stat screen|sign[- ]?in|gacha|level(s|ing)? up|quest log)\\b"),
        FicType.TRANSMIGRATION to rx("\\b(transmigrat\\w*|soul transfer|woke up (in|as)|wakes up (in|as)|in the body of|body swap into|possess(es|ed)? the body)\\b"),
        FicType.OC to rx("\\b(oc|o\\.c\\.|original character|oc[!-]\\w+|my oc)\\b")
    )

    /** Texte sur lequel on cherche les indices : titre, résumé, étiquettes et genres. */
    fun haystack(f: Fic): String = buildString {
        append(f.title).append(" \u0001 ").append(f.summary).append(" \u0001 ")
        append(f.tags.joinToString(" , ")).append(" \u0001 ").append(f.genres.joinToString(" , "))
    }

    fun detect(f: Fic): Set<FicType> {
        val h = haystack(f)
        val out = LinkedHashSet<FicType>()
        for ((t, r) in PATTERNS) if (r.containsMatchIn(h)) out += t
        if (f.crossover || f.fandoms.size > 1) out += FicType.CROSSOVER
        // « SI » est très court : on ne le garde dans le résumé que s'il est en majuscules
        if (FicType.SELF_INSERT in out && !Regex("self[- ]?insert|insert", RegexOption.IGNORE_CASE).containsMatchIn(h) &&
            !Regex("\\bSI\\b|\\bS\\.I\\.").containsMatchIn(h) && f.tags.none { it.equals("si", true) || it.contains("self insert", true) }
        ) out -= FicType.SELF_INSERT
        // « OC » seul dans un résumé en minuscules est rare : même prudence
        if (FicType.OC in out && !Regex("\\bOC\\b|original character|O\\.C\\.").containsMatchIn(h) && f.tags.none { it.equals("oc", true) }) out -= FicType.OC
        // « system » en passant (« solar system ») ne suffit pas sans autre indice de jeu
        if (FicType.SYSTEM in out && !Regex("\\b(the |a |my |his |her )?system\\b.{0,40}(ding|host|quest|reward|points?|level|status|gamer)|gamer|litrpg|status window|sign[- ]?in|\\[system\\]|system:|\\bsystem\\b(?! of)", RegexOption.IGNORE_CASE).containsMatchIn(h)) out -= FicType.SYSTEM
        return out
    }
}
