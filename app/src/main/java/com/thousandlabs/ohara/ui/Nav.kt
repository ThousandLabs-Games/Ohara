package com.thousandlabs.ohara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.discover.SearchQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Les écrans de l'appli. L'accueil porte les quatre onglets du bas. */
sealed class Screen {
    data object Home : Screen()
    /** Fiche d'une fic ; [preview] sert pour un résultat de recherche pas encore dans la bibliothèque. */
    data class FicDetail(val ficId: String, val preview: Fic? = null) : Screen()
    data class Reader(val ficId: String, val chapter: Int? = null) : Screen()
    data class Results(val query: SearchQuery) : Screen()
    data class Similar(val fic: Fic) : Screen()
    data object Add : Screen()
    data object Downloads : Screen()
    data object Stats : Screen()
    data object Guide : Screen()
    data object WhatsNew : Screen()
    data object Hidden : Screen()
    data object Duplicates : Screen()
    data object HistoryScreen : Screen()
    data class Settings(val section: String) : Screen()
}

object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableIntStateOf(0)

    const val TAB_LIBRARY = 0
    const val TAB_DISCOVER = 1
    const val TAB_BROWSER = 2
    const val TAB_SETTINGS = 3

    val top: Screen get() = stack.last()

    fun push(s: Screen) {
        // pas deux fois le même écran à la suite
        if (stack.lastOrNull() == s) return
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replace(s: Screen) {
        if (stack.size > 1) stack[stack.lastIndex] = s else stack.add(s)
    }

    fun home(tab: Int) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        this.tab = tab
    }

    fun openBrowser(url: String) {
        BrowserHolder.pendingUrl = url
        home(TAB_BROWSER)
    }
}

/** Messages courts affichés en bas de l'écran. */
object Toast {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    fun show(msg: String) { _events.tryEmit(msg) }
}

/** Tâches lancées depuis l'interface qui doivent continuer même si l'écran change. */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
