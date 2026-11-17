package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Score « Prometteuse » (0 à 100) : une fic qui attire beaucoup de lecteurs pour sa longueur et son âge,
 * qui est encore suivie et assez longue pour qu'on s'y attache. Chaque site donne des chiffres différents :
 * on compare chaque fic aux habitudes de son propre site.
 */
object Scoring {
    private const val DAY = 24L * 3600 * 1000

    data class Detail(val score: Int, val reasons: List<String>)

    private fun l(x: Double) = ln(1.0 + max(0.0, x))

    /** Courbe en S : transforme une valeur brute en score de 0 à 100 autour d'un point médian. */
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
            Site.WN -> {
                0.6 * l(f.views / months / 300) + 0.4 * l(f.favs / months / 3)
            }
            Site.OTHER -> 1.6
        }
        val base = squash(interest, mid = 2.6, spread = 0.75)
        if (base >= 70) reasons += "Beaucoup de lecteurs pour son âge"

        // 2. Longueur : de quoi s'attacher, sans exiger un pavé
        val lengthBonus = when {
            kWords >= 300 -> 8.0
            kWords >= 100 -> 7.0
            kWords >= 40 -> 4.0
            kWords >= 15 -> 0.0
            kWords >= 5 -> -8.0
            else -> -18.0
        }
        if (kWords >= 100) reasons += "Longue (${kWords.toInt()} k mots)"

        // 3. Activité : suivie récemment, ou bien terminée
        val sinceUpdate = if (f.updated > 0) (now - f.updated).toDouble() / DAY else 400.0
        val inactiveDays = Prefs.inactiveMonths * 30.0
        val activity = when {
            f.status == FicStatus.COMPLETE -> { reasons += "Terminée"; 6.0 }
            sinceUpdate <= 30 -> { reasons += "Mise à jour ce mois-ci"; 6.0 }
            sinceUpdate <= 120 -> 2.0
            sinceUpdate <= inactiveDays -> -3.0
            else -> -12.0
        }

        // 4. Jeune fic qui démarre fort : c'est tout l'intérêt du score
        val rising = if (ageDays <= 90 && base >= 60) { reasons += "Démarrage fort"; 6.0 } else 0.0

        val s = (base * 0.82 + lengthBonus + activity + rising).coerceIn(0.0, 100.0)
        return Detail(s.toInt(), reasons)
    }

    fun label(score: Int): String? = when {
        score >= 78 -> "Très prometteuse"
        score >= 62 -> "Prometteuse"
        else -> null
    }

    /** Fic inactive : en cours, sans nouveau chapitre depuis le délai réglé. */
    fun isInactive(f: Fic, now: Long = System.currentTimeMillis()): Boolean =
        f.status != FicStatus.COMPLETE && f.updated > 0 && now - f.updated > Prefs.inactiveMonths * 30L * DAY

    fun minWords(a: Long, b: Long) = min(a, b)
}
