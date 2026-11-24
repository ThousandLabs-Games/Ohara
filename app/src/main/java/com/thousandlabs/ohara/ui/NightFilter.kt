package com.thousandlabs.ohara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.thousandlabs.ohara.data.Prefs
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Filtre de nuit : une teinte chaude et une légère pénombre posées sur tout l'écran d'Ohara, pour lire le soir
 * sans fatiguer les yeux. Le filtre laisse passer les gestes (rien ne change dans la page elle-même).
 */
object NightFilter {
    const val OFF = 0; const val ALWAYS = 1; const val EVENING = 2

    var mode: Int get() = Prefs.getInt("night_mode", OFF); set(v) { Prefs.putInt("night_mode", v); Prefs.remove("night_skip"); Prefs.remove("night_force") }
    /** Chaleur de la teinte, de 0 à 1. */
    var warmth: Float get() = Prefs.getFloat("night_warm", 0.5f); set(v) = Prefs.putFloat("night_warm", v)
    /** Pénombre, de 0 à 1. */
    var dim: Float get() = Prefs.getFloat("night_dim", 0.25f); set(v) = Prefs.putFloat("night_dim", v)
    var fromHour: Int get() = Prefs.getInt("night_from", 21); set(v) = Prefs.putInt("night_from", v)
    var toHour: Int get() = Prefs.getInt("night_to", 6); set(v) = Prefs.putInt("night_to", v)

    private fun inEvening(h: Int): Boolean {
        val a = fromHour; val b = toHour
        return if (a == b) true else if (a < b) h in a until b else h >= a || h < b
    }

    /** Prochaine fin de soirée (heure de fin du filtre). */
    private fun nextEnd(): Long {
        val c = Calendar.getInstance()
        val end = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, toHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        if (!end.after(c)) end.add(Calendar.DAY_OF_YEAR, 1)
        return end.timeInMillis
    }

    fun activeNow(): Boolean {
        val now = System.currentTimeMillis()
        if (Prefs.getLong("night_force", 0L) > now) return true
        return when (mode) {
            ALWAYS -> true
            EVENING -> inEvening(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) && Prefs.getLong("night_skip", 0L) <= now
            else -> false
        }
    }

    /** Bouton du menu du navigateur : allume ou éteint le filtre pour ce soir, sans changer le réglage choisi. */
    fun toggle() {
        if (activeNow()) {
            Prefs.remove("night_force")
            if (mode == ALWAYS) mode = OFF else Prefs.putLong("night_skip", nextEnd())
            Toast.show("Filtre de nuit éteint.")
        } else {
            Prefs.remove("night_skip")
            Prefs.putLong("night_force", nextEnd())
            Toast.show("Filtre de nuit allumé jusqu'à ${toHour} h. Réglages › Lecture pour l'intensité.")
        }
    }
}

@Composable
fun NightOverlay() {
    val pv by Prefs.version.collectAsState()
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); minute++ } }
    @Suppress("UNUSED_EXPRESSION") pv
    @Suppress("UNUSED_EXPRESSION") minute
    if (!NightFilter.activeNow()) return
    val w = NightFilter.warmth.coerceIn(0f, 1f)
    val d = NightFilter.dim.coerceIn(0f, 1f)
    if (w > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8F2E).copy(alpha = 0.06f + w * 0.26f)))
    if (d > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = d * 0.55f)))
}
