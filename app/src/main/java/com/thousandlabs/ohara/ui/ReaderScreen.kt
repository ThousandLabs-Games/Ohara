package com.thousandlabs.ohara.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thousandlabs.ohara.MainActivity
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Highlight
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.ReadingLog
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.net.VerificationNeeded
import com.thousandlabs.ohara.translate.Translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Vitesse de lecture mesurée (mots par minute), 230 par défaut tant qu'il n'y a pas assez de mesures. */
fun readingSpeed(): Int {
    val days = ReadingLog.snapshot().values
    val words = days.sumOf { it[0] }
    val secs = days.sumOf { it[1] }
    return if (secs > 900 && words > 2000) (words * 60 / secs).toInt().coerceIn(120, 700) else 230
}

private class Bridge(
    val onReady: () -> Unit,
    val onProgress: (Int, Float, Float, Float) -> Unit,
    val onNeedNext: (Int) -> Unit,
    val onSelection: (String, Int, String) -> Unit,
    val onTap: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun ready() = main.post { onReady() }.let { }
    @JavascriptInterface fun progress(ch: Int, pos: Float, frac: Float, end: Float) = main.post { onProgress(ch, pos, frac, end) }.let { }
    @JavascriptInterface fun needNext(last: Int) = main.post { onNeedNext(last) }.let { }
    @JavascriptInterface fun selection(text: String, ch: Int, para: String) = main.post { onSelection(text, ch, para) }.let { }
    @JavascriptInterface fun tap() = main.post { onTap() }.let { }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(ficId: String, startChapter: Int?) {
    val context = LocalContext.current
    val lib by Library.fics.collectAsState()
    val pv by Prefs.version.collectAsState()
    val fic = lib.firstOrNull { it.id == ficId }
    if (fic == null) { LaunchedEffect(Unit) { Nav.pop() }; return }

    var requestCh by remember { mutableIntStateOf((startChapter ?: fic.progressChapter).coerceIn(0, (fic.totalChapters - 1).coerceAtLeast(0))) }
    var restorePos by remember { mutableFloatStateOf(if (startChapter == null || startChapter == fic.progressChapter) fic.progressPos else 0f) }
    var displayCh by remember { mutableIntStateOf(requestCh) }
    var chFrac by remember { mutableFloatStateOf(0f) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var verifyUrl by remember { mutableStateOf<String?>(null) }
    var bars by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf(false) }
    var selText by remember { mutableStateOf("") }
    var selCh by remember { mutableIntStateOf(-1) }
    var selPara by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var maskDialog by remember { mutableStateOf(false) }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val counted = remember { HashSet<Int>() }
    val chapterWords = remember { HashMap<Int, Int>() }
    val web = remember { mutableStateOf<WebView?>(null) }
    var fr by remember { mutableStateOf(Translate.enabled) }
    var translating by remember { mutableStateOf<String?>(null) }
    val theme = ReaderPage.theme().also { @Suppress("UNUSED_EXPRESSION") pv }

    // ouverture : badges remis à zéro
    LaunchedEffect(ficId) {
        Library.update(ficId) { it.copy(newChapters = 0, completedAlert = false, lastOpened = System.currentTimeMillis(),
            shelves = if (Shelves.DONE in it.shelves || Shelves.ABANDONED in it.shelves) it.shelves else it.shelves + Shelves.READING - Shelves.TO_READ) }
    }

    // écran allumé, touches de volume
    DisposableEffect(Unit) {
        (context as? MainActivity)?.keepScreenOn(Prefs.keepScreenOn)
        ReaderKeys.handler = { down -> web.value?.evaluateJavascript("ohara.page(${if (down) 1 else -1})", null) }
        onDispose {
            (context as? MainActivity)?.keepScreenOn(false)
            ReaderKeys.handler = null
            Library.saveNow()
        }
    }

    // temps de lecture : compté quand vous êtes actif (défilement dans les 90 dernières secondes)
    LaunchedEffect(ficId) {
        while (true) {
            delay(10_000)
            if (System.currentTimeMillis() - lastActivity < 90_000) {
                ReadingLog.add(0, 10)
                Library.update(ficId) { it.copy(secondsRead = it.secondsRead + 10) }
            }
        }
    }

    suspend fun sectionJson(f: Fic, idx: Int): JSONObject {
        val raw = Downloads.ensureChapter(f, idx)
        val cur = Library.get(f.id) ?: f
        var html = withContext(Dispatchers.Default) { Masking.applyToHtml(cur, raw).html }
        chapterWords[idx] = Library.countWords(raw)
        var title = cur.chapters.getOrNull(idx)?.title ?: "Chapitre ${idx + 1}"
        if (fr) {
            try {
                html = Translate.chapter(f.id, idx, html) { msg -> translating = msg }
                title = runCatching { Translate.text(title) }.getOrDefault(title)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.show("Traduction impossible (${e.message ?: "pas de connexion pour télécharger le modèle"}) : texte original affiché.")
            } finally { translating = null }
        }
        val hl = JSONArray()
        cur.highlights.filter { it.chapter == idx }.forEach { hl.put(JSONObject().put("t", it.text).put("n", it.note)) }
        val total = cur.totalChapters
        return JSONObject().put("ch", idx).put("title", title)
            .put("html", Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("hl", hl)
            .put("end", if (idx >= total - 1) "Dernier chapitre disponible" else "Fin du chapitre ${idx + 1} sur $total")
    }

    // chargement du chapitre demandé
    LaunchedEffect(requestCh, reloadKey, ready, fr) {
        if (!ready) return@LaunchedEffect
        loading = true; error = null; verifyUrl = null
        val f = Library.get(ficId) ?: return@LaunchedEffect
        try {
            val json = sectionJson(f, requestCh)
            web.value?.evaluateJavascript("ohara.show($json, $restorePos, ${Prefs.readerContinuous})", null)
            displayCh = requestCh
            Library.update(ficId) { it.copy(progressChapter = requestCh, lastOpened = System.currentTimeMillis()) }
            loading = false
            // le chapitre suivant est préparé à l'avance (lecture hors connexion)
            val next = requestCh + 1
            if (next < (Library.get(ficId)?.totalChapters ?: 0) && !Library.hasChapter(ficId, next)) {
                runCatching { Downloads.ensureChapter(Library.get(ficId)!!, next) }
            }
        } catch (e: VerificationNeeded) {
            loading = false; error = "Le site demande une vérification « Je ne suis pas un robot »."; verifyUrl = e.url
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loading = false; error = e.message ?: "Chapitre illisible."
        }
    }

    fun goTo(idx: Int) {
        val total = (Library.get(ficId)?.totalChapters ?: 0)
        if (idx < 0 || idx >= total) return
        if (idx > displayCh) Library.update(ficId) { it.copy(maxChapterRead = maxOf(it.maxChapterRead, displayCh)) }
        restorePos = 0f
        requestCh = idx
    }

    Box(Modifier.fillMaxSize().background(theme.bgColor)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = false
                    this.settings.allowFileAccess = false
                    this.settings.allowContentAccess = false
                    this.settings.blockNetworkLoads = true
                    this.settings.textZoom = 100
                    setBackgroundColor(android.graphics.Color.parseColor(theme.bg))
                    isVerticalScrollBarEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("ohara.style(${ReaderPage.style()})", null)
                        }
                    }
                    addJavascriptInterface(Bridge(
                        onReady = { ready = true },
                        onProgress = { ch, pos, frac, end ->
                            lastActivity = System.currentTimeMillis()
                            displayCh = ch; chFrac = frac
                            Library.update(ficId) { f ->
                                var max = f.maxChapterRead
                                if (end >= 0.97f) max = maxOf(max, ch)
                                f.copy(progressChapter = ch, progressPos = pos, maxChapterRead = max)
                            }
                            if (end >= 0.97f && counted.add(ch)) {
                                val w = chapterWords[ch] ?: 0
                                if (w > 0) {
                                    ReadingLog.add(w.toLong(), 0)
                                    Library.update(ficId) { it.copy(wordsRead = it.wordsRead + w) }
                                }
                            }
                        },
                        onNeedNext = { last ->
                            val f = Library.get(ficId)
                            if (f == null || last + 1 >= f.totalChapters) { web.value?.evaluateJavascript("ohara.endOfBook()", null) }
                            else AppScope.launch {
                                val json = runCatching { sectionJson(f, last + 1) }.getOrNull()
                                web.value?.evaluateJavascript("ohara.append(${json ?: "null"})", null)
                            }
                        },
                        onSelection = { t, ch, para -> selText = t; selCh = ch; selPara = para; if (t.isNotEmpty()) bars = false },
                        onTap = { bars = !bars }
                    ), "Ohara")
                    loadDataWithBaseURL("https://lecteur.ohara/", ReaderPage.shell(), "text/html", "utf-8", null)
                    web.value = this
                }
            }
        )
        val styleJson = ReaderPage.style().toString()
        LaunchedEffect(styleJson, ready) {
            web.value?.setBackgroundColor(android.graphics.Color.parseColor(theme.bg))
            web.value?.evaluateJavascript("if(window.ohara)ohara.style($styleJson)", null)
        }

        if (loading) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = theme.fgColor.copy(alpha = 0.6f))
            translating?.let { Spacer(Modifier.height(12.dp)); Text(it, color = theme.fgColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall) }
        }
        error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(32.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chapitre indisponible", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { reloadKey++ }) { Text("Réessayer") }
                    val u = verifyUrl ?: fic.chapters.getOrNull(requestCh)?.url ?: fic.url
                    OutlinedButton(onClick = { if (verifyUrl != null) LinkFlow.verifyThenRead(ficId, fic.chapters.getOrNull(requestCh)?.url ?: u) else Nav.openBrowser(u) }) { Text(if (verifyUrl != null) "Vérifier" else "Ouvrir le site") }
                }
            }
        }

        // barre du haut
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = theme.bgColor.copy(alpha = 0.97f), contentColor = theme.fgColor, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                    Column(Modifier.weight(1f)) {
                        Text(fic.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fic.chapters.getOrNull(displayCh)?.title ?: "Chapitre ${displayCh + 1}", style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.fgColor.copy(alpha = 0.65f))
                    }
                    IconButton(onClick = {
                        fr = !fr; Translate.enabled = fr
                        restorePos = Library.get(ficId)?.progressPos ?: 0f
                        requestCh = displayCh
                        Toast.show(if (fr) "Traduction en français activée (sur le téléphone, sans connexion une fois le modèle téléchargé)." else "Texte original affiché.")
                    }) { Icon(Icons.Outlined.Translate, if (fr) "Afficher le texte original" else "Traduire en français", tint = if (fr) MaterialTheme.colorScheme.primary else theme.fgColor) }
                    IconButton(onClick = { chapters = true }) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Chapitres") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.TextFields, "Affichage") }
                    IconButton(onClick = { Nav.push(Screen.FicDetail(ficId)) }) { Icon(Icons.Outlined.Info, "Fiche de la fic") }
                }
            }
        }

        // barre du bas : chapitres et temps restant
        AnimatedVisibility(bars && selText.isEmpty(), enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = theme.bgColor.copy(alpha = 0.97f), contentColor = theme.fgColor, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val total = fic.totalChapters
                    val wpm = remember(displayCh) { readingSpeed() }
                    val chWords = chapterWords[displayCh] ?: fic.chapters.getOrNull(displayCh)?.words?.takeIf { it > 0 } ?: (if (total > 0) (fic.words / total).toInt() else 0)
                    val leftCh = (chWords * (1f - chFrac)).toLong()
                    val avg = if (total > 0 && fic.words > 0) fic.words / total else chWords.toLong()
                    val leftBook = leftCh + (total - displayCh - 1).coerceAtLeast(0) * avg
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { goTo(displayCh - 1) }, enabled = displayCh > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Chapitre précédent") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Chapitre ${displayCh + 1} sur $total", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${Fmt.duration(leftCh * 60 / wpm)} dans ce chapitre · ${Fmt.duration(leftBook * 60 / wpm)} au total",
                                style = MaterialTheme.typography.labelSmall, color = theme.fgColor.copy(alpha = 0.65f)
                            )
                        }
                        IconButton(onClick = { goTo(displayCh + 1) }, enabled = displayCh < total - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Chapitre suivant") }
                    }
                }
            }
        }

        // sélection : surligner, noter, masquer ce genre de passage
        AnimatedVisibility(selText.isNotEmpty(), enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp, modifier = Modifier.navigationBarsPadding().padding(12.dp), shape = RoundedCornerShape(14.dp)) {
                FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val ch = if (selCh >= 0) selCh else displayCh
                        Library.update(ficId) { it.copy(highlights = it.highlights + Highlight(ch, selText)) }
                        web.value?.evaluateJavascript("ohara.highlight($ch, ${JSONObject.quote(selText)}, '')", null)
                        selText = ""
                    }) { Icon(Icons.Outlined.Highlight, null); Spacer(Modifier.width(6.dp)); Text("Surligner") }
                    TextButton(onClick = { noteDialog = true }) { Icon(Icons.Outlined.NoteAdd, null); Spacer(Modifier.width(6.dp)); Text("Note") }
                    TextButton(onClick = { maskDialog = true }) { Icon(Icons.Outlined.VisibilityOff, null); Spacer(Modifier.width(6.dp)); Text("Masquer ce genre de passage") }
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ohara", selText))
                        web.value?.evaluateJavascript("ohara.clearSelection()", null); selText = ""
                    }) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copier") }
                }
            }
        }
    }

    if (noteDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("Surligner avec une note") },
            text = {
                Column {
                    Text("« ${selText.take(240)} »", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Votre note") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ch = if (selCh >= 0) selCh else displayCh
                    Library.update(ficId) { it.copy(highlights = it.highlights + Highlight(ch, selText, note.trim())) }
                    web.value?.evaluateJavascript("ohara.highlight($ch, ${JSONObject.quote(selText)}, ${JSONObject.quote(note.trim())})", null)
                    selText = ""; noteDialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("Annuler") } }
        )
    }

    if (maskDialog) {
        val base = if (selText.length >= 12) selText else selPara.ifBlank { selText }
        var phrase by remember { mutableStateOf(base.replace(Regex("\\s+"), " ").trim().take(160)) }
        var scope by remember { mutableStateOf(Masking.Scope.AUTHOR) }
        AlertDialog(
            onDismissRequest = { maskDialog = false },
            title = { Text("Masquer ce genre de passage") },
            text = {
                Column {
                    Text("Tout paragraphe qui contient ce texte sera replié (promos Patreon, notes d'auteur, appels aux votes…). Gardez une phrase courte et caractéristique.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(phrase, { phrase = it }, label = { Text("Texte repère") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Masking.Scope.entries.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = phrase.trim().length >= 4, onClick = {
                    Masking.add(phrase, scope, Library.get(ficId) ?: fic)
                    maskDialog = false; selText = ""
                    restorePos = Library.get(ficId)?.progressPos ?: 0f
                    reloadKey++
                    Toast.show("Passage masqué. Gérez les règles dans Réglages › Passages masqués.")
                }) { Text("Masquer") }
            },
            dismissButton = { TextButton(onClick = { maskDialog = false }) { Text("Annuler") } }
        )
    }

    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        ReaderSettings()
    }

    if (chapters) ModalBottomSheet(onDismissRequest = { chapters = false }) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (displayCh - 3).coerceAtLeast(0))
        Text("Chapitres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            itemsIndexed(fic.chapters) { i, c ->
                Row(Modifier.fillMaxWidth().clickable { chapters = false; goTo(i) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(40.dp))
                    Text(c.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (i == displayCh) MaterialTheme.colorScheme.primary else if (i <= fic.maxChapterRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (Library.hasChapter(ficId, i)) Icon(Icons.Outlined.CheckCircle, "Téléchargé", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Réglages d'affichage du lecteur (appliqués tout de suite). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettings() {
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Affichage", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Thème", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderPage.THEMES.forEachIndexed { i, t ->
                val sel = Prefs.readerTheme == i
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Prefs.readerTheme = i }) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(t.bgColor)
                        .then(if (sel) Modifier.background(Color.Transparent) else Modifier), contentAlignment = Alignment.Center) {
                        Text("Aa", color = t.fgColor, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Police", style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderPage.FONTS.forEachIndexed { i, (name, _) -> FilterChip(selected = Prefs.readerFont == i, onClick = { Prefs.readerFont = i }, label = { Text(name) }) }
        }
        SliderRowCompact("Taille", Prefs.readerSize.toFloat(), 12f..32f, 19, "${Prefs.readerSize} px") { Prefs.readerSize = it.toInt() }
        SliderRowCompact("Interligne", Prefs.readerLine, 1.1f..2.4f, 12, String.format(java.util.Locale.FRANCE, "%.1f", Prefs.readerLine)) {
            Prefs.readerLine = (Math.round(it * 10) / 10f)
        }
        SliderRowCompact("Marges", Prefs.readerMargin.toFloat(), 4f..48f, 10, "${Prefs.readerMargin} px") { Prefs.readerMargin = it.toInt() }
        ToggleLine("Texte justifié", Prefs.readerJustify) { Prefs.readerJustify = it }
        ToggleLine("Lecture continue (les chapitres s'enchaînent)", Prefs.readerContinuous) { Prefs.readerContinuous = it }
        ToggleLine("Garder l'écran allumé", Prefs.keepScreenOn) { Prefs.keepScreenOn = it }
        ToggleLine("Touches de volume pour tourner les pages", Prefs.volumeKeys) { Prefs.volumeKeys = it }
        Text("Touchez le haut ou le bas de l'écran pour avancer d'une page, le milieu pour afficher les barres. Appui long sur un mot pour surligner ou masquer un passage.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SliderRowCompact(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp).padding(start = 8.dp))
    }
}

@Composable
private fun ToggleLine(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
