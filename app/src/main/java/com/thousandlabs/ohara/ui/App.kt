package com.thousandlabs.ohara.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.ohara.MainActivity
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.Discover

/** Verrouillage par empreinte : l'appli se reverrouille après 30 secondes en arrière-plan. */
object Locker {
    var activity: MainActivity? = null
    var locked by mutableStateOf(false)
    private var backgroundAt = 0L
    private var firstStart = true

    fun onStart() {
        if (Prefs.appLock && (firstStart || System.currentTimeMillis() - backgroundAt > 30_000)) locked = true
        firstStart = false
        if (locked) unlock()
    }

    fun onStop() { backgroundAt = System.currentTimeMillis() }

    fun unlock() { activity?.authenticate { locked = false } }
}


@Composable
fun OharaRoot() {
    OharaTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { Toast.events.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }
        val holder = rememberSaveableStateHolder()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                BackHandler(enabled = Nav.stack.size > 1) { Nav.pop() }
                val depth = Nav.stack.size
                androidx.compose.animation.AnimatedContent(
                    targetState = Nav.top to depth,
                    transitionSpec = {
                        val forward = targetState.second >= initialState.second
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { w -> if (forward) w / 8 else -w / 8 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                    },
                    label = "ecrans"
                ) { (s, d) ->
                holder.SaveableStateProvider(s.toString().take(300) + "#" + d) {
                    when (s) {
                        Screen.Home -> BrowserHome()
                        Screen.Library -> LibraryTab()
                        Screen.Discover -> DiscoverTab()
                        Screen.SettingsMain -> SettingsTab()
                        is Screen.FicDetail -> FicScreen(s.ficId, s.preview)
                        is Screen.Results -> ResultsScreen(s.query)
                        is Screen.Similar -> SimilarScreen(s.fic)
                        Screen.Add -> AddScreen()
                        Screen.Downloads -> DownloadsScreen()
                        Screen.Stats -> StatsScreen()
                        Screen.Guide -> GuideScreen()
                        Screen.WhatsNew -> WhatsNewScreen()
                        Screen.Hidden -> HiddenScreen()
                        Screen.Duplicates -> DuplicatesScreen()
                        Screen.HistoryScreen -> HistoryScreen()
                        is Screen.Settings -> SettingsDetailScreen(s.section)
                    }
                }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
                if (Locker.locked) LockScreen()
            }
        }
    }
}

@Composable
private fun LockScreen() {
    BackHandler(true) { Locker.activity?.moveTaskToBack(true) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ohara", fontFamily = FontFamily.Serif, fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { Locker.unlock() }) { Text("Déverrouiller") }
        }
    }
}
