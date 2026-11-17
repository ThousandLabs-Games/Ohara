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
    data object Library : Screen()
    data object Discover : Screen()
    data object SettingsMain : Screen()
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

    const val TAB_LIBRARY = 0
    const val TAB_DISCOVER = 1
    const val TAB_BROWSER = 2
    const val TAB_SETTINGS = 3

    val top: Screen get() = stack.last()

    fun push(s: Screen) {
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

    /** Revient au navigateur ; avec un onglet autre que le navigateur, ouvre cet écran par-dessus. */
    fun home(tab: Int = TAB_BROWSER) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        when (tab) {
            TAB_LIBRARY -> stack.add(Screen.Library)
            TAB_DISCOVER -> stack.add(Screen.Discover)
            TAB_SETTINGS -> stack.add(Screen.SettingsMain)
        }
    }

    /** Ouvre une adresse dans le navigateur (onglet actuel s'il est vide, sinon un nouvel onglet). */
    fun openBrowser(url: String, newTab: Boolean = true) {
        home(TAB_BROWSER)
        BrowserHolder.openUrl(url, newTab)
    }

    /** Ouvre une fic de la bibliothèque dans le navigateur, là où vous vous êtes arrêté (ou au chapitre demandé). */
    fun openFic(ficId: String, chapter: Int? = null) {
        val f = com.thousandlabs.ohara.data.Library.get(ficId) ?: return
        val src = com.thousandlabs.ohara.sources.Sources.of(f.site)
        val url = when {
            chapter != null -> f.chapters.getOrNull(chapter)?.url ?: src.chapterUrl(f, chapter)
            f.resumeUrl.isNotBlank() -> f.resumeUrl
            else -> f.chapters.getOrNull(f.progressChapter)?.url ?: runCatching { src.chapterUrl(f, f.progressChapter) }.getOrDefault(f.url)
        }
        com.thousandlabs.ohara.data.Library.update(ficId) { it.copy(lastOpened = System.currentTimeMillis(), newChapters = 0, completedAlert = false) }
        openBrowser(url.ifBlank { f.url })
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
