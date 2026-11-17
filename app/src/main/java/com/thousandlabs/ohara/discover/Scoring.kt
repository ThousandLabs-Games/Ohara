package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Score « Prometteuse » (0 à 100). Quatre critères, tous réglables dans Réglages › Score « Prometteuse » :
 *
 * 1. Intérêt des lecteurs, rapporté à l'âge et à la longueur de la fic (chaque site comparé à lui-même).
 * 2. Longueur : de quoi s'attacher.
 * 3. Activité : mise à jour récemment, ou terminée.
 * 4. Démarrage fort : une jeune fic qui attire déjà beaucoup.
 */
object Scoring {
    private const val DAY = 24L * 3600 * 1000

    // ---------- Réglages (valeurs par défaut entre parenthèses) ----------

    /** Part de l'intérêt des lecteurs dans le score, en % (82). */
    var wInterest: Int get() = Prefs.getInt("sc_interest", 82); set(v) = Prefs.putInt("sc_interest", v)
    /** Importance de la longueur, en % du barème normal (100). */
    var wLength: Int get() = Prefs.getInt("sc_length", 100); set(v) = Prefs.putInt("sc_length", v)
    /** Importance de l'activité, en % du barème normal (100). */
    var wActivity: Int get() = Prefs.getInt("sc_activity", 100); set(v) = Prefs.putInt("sc_activity", v)
    /** Importance du démarrage fort, en % du barème normal (100). */
    var wRising: Int get() = Prefs.getInt("sc_rising", 100); set(v) = Prefs.putInt("sc_rising", v)
    /** Longueur en dessous de laquelle une fic est pénalisée, en milliers de mots (15). */
    var minKWords: Int get() = Prefs.getInt("sc_min_k", 15); set(v) = Prefs.putInt("sc_min_k", v)
    /** Longueur à partir de laquelle une fic a le bonus maximal, en milliers de mots (100). */
    var bigKWords: Int get() = Prefs.getInt("sc_big_k", 100); set(v) = Prefs.putInt("sc_big_k", v)
    /** Âge maximal pour un « démarrage fort », en jours (90). */
    var risingDays: Int get() = Prefs.getInt("sc_rising_days", 90); set(v) = Prefs.putInt("sc_rising_days", v)
    /** Seuil de « Prometteuse » (62) et de « Très prometteuse » (78). */
    var threshold: Int get() = Prefs.getInt("sc_threshold", 62); set(v) = Prefs.putInt("sc_threshold", v)
    var veryThreshold: Int get() = Prefs.getInt("sc_very", 78); set(v) = Prefs.putInt("sc_very", v)

    fun reset() {
        listOf("sc_interest", "sc_length", "sc_activity", "sc_rising", "sc_min_k", "sc_big_k", "sc_rising_days", "sc_threshold", "sc_very")
            .forEach { Prefs.remove(it) }
    }

    /** Détail du calcul : chaque critère avec les points qu'il apporte. */
    data class Detail(val score: Int, val reasons: List<String>, val parts: List<Pair<String, Int>> = emptyList())

    private fun l(x: Double) = ln(1.0 + max(0.0, x))

    /** Courbe en S : transforme une valeur brute en note de 0 à 100 autour d'un point médian. */
    private fun squash(raw: Double, mid: Double, spread: Double): Double = 100.0 / (1.0 + exp(-(raw - mid) / spread))

    fun score(f: Fic, now: Long = System.currentTimeMillis()): Int = detail(f, now).score

    fun detail(f: Fic, now: Long = System.currentTimeMillis()): Detail {
        val reasons = mutableListOf<String>()
        val start = if (f.published > 0) f.published else f.updated
        val ageDays = if (start > 0) max(7.0, (now - start).toDouble() / DAY) else 365.0
        val months = ageDays / 30.0
        val chapters = max(1, f.totalChapters).toDouble()
        val kWords = if (f.words > 0) f.words / 1000.0 else chapters * 3.0

        // 1. Intérêt des lecteurs, ramené à l'âge et à la longueur
        val interest: Double = when (f.site) {
            Site.FFN -> {
                val fans = f.favs + 0.8 * f.follows
                0.55 * l(fans / months * 3) + 0.30 * l(fans / max(1.0, kWords / 10)) + 0.15 * l(f.reviews / chapters * 4)
            }
            Site.SB -> {
                val v = if (f.views > 0) f.views.toDouble() else f.replies * 25.0
                0.5 * l(v / months / 40) + 0.3 * l(f.replies / chapters) + 0.2 * l(f.likes / chapters / 3)
            }
            Site.WN -> 0.6 * l(f.views / months / 300) + 0.4 * l(f.favs / months / 3)
            Site.OTHER -> 1.6
        }
        val base = squash(interest, mid = 2.6, spread = 0.75)
        if (base >= 70) reasons += "Beaucoup de lecteurs pour son âge"
        val interestPts = base * wInterest / 100.0

        // 2. Longueur
        val big = bigKWords.coerceAtLeast(minKWords + 1).toDouble()
        val min = minKWords.toDouble()
        val lengthRaw = when {
            kWords >= big * 3 -> 8.0
            kWords >= big -> 7.0
            kWords >= (min + big) / 2.5 -> 4.0
            kWords >= min -> 0.0
            kWords >= min / 3 -> -8.0
            else -> -18.0
        }
        val lengthPts = lengthRaw * wLength / 100.0
        if (kWords >= big) reasons += "Longue (${kWords.toInt()} k mots)"

        // 3. Activité
        val sinceUpdate = if (f.updated > 0) (now - f.updated).toDouble() / DAY else 400.0
        val inactiveDays = Prefs.inactiveMonths * 30.0
        val activityRaw = when {
            f.status == FicStatus.COMPLETE -> { reasons += "Terminée"; 6.0 }
            sinceUpdate <= 30 -> { reasons += "Mise à jour ce mois-ci"; 6.0 }
            sinceUpdate <= 120 -> 2.0
            sinceUpdate <= inactiveDays -> -3.0
            else -> -12.0
        }
        val activityPts = activityRaw * wActivity / 100.0

        // 4. Démarrage fort
        val risingPts = if (ageDays <= risingDays && base >= 60) { reasons += "Démarrage fort"; 6.0 * wRising / 100.0 } else 0.0

        val s = (interestPts + lengthPts + activityPts + risingPts).coerceIn(0.0, 100.0)
        val parts = listOf(
            "Intérêt des lecteurs" to interestPts.toInt(),
            "Longueur" to lengthPts.toInt(),
            "Activité" to activityPts.toInt(),
            "Démarrage fort" to risingPts.toInt()
        )
        return Detail(s.toInt(), reasons, parts)
    }

    fun label(score: Int): String? = when {
        score >= veryThreshold -> "Très prometteuse"
        score >= threshold -> "Prometteuse"
        else -> null
    }

    fun isPromising(score: Int) = score >= threshold

    /** Fic inactive : en cours, sans nouveau chapitre depuis le délai réglé. */
    fun isInactive(f: Fic, now: Long = System.currentTimeMillis()): Boolean =
        f.status != FicStatus.COMPLETE && f.updated > 0 && now - f.updated > Prefs.inactiveMonths * 30L * DAY
}
