package com.thousandlabs.ohara.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.export.Exporter
import com.thousandlabs.ohara.download.DownloadService
import com.thousandlabs.ohara.download.Notify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Envoi en un geste à l'appli Claude : les fichiers du pack et une consigne prête. */
object ClaudeSend {
    const val PACKAGE = "com.anthropic.claude"

    fun send(context: Context, files: List<File>, prompt: String) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fichiers", it) })
        val clip = ClipData.newRawUri(files.first().name, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        fun intent() = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, prompt)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // La consigne est aussi copiée : certaines applis ne reprennent pas le texte quand il y a des fichiers.
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Consigne pour Claude", prompt))
        }
        try {
            context.startActivity(intent().setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.show("Envoyé à Claude. La consigne est aussi copiée : collez-la si elle n'apparaît pas.")
        } catch (_: ActivityNotFoundException) {
            Toast.show("L'appli Claude n'est pas installée : choisissez où envoyer les fichiers.")
            context.startActivity(Intent.createChooser(intent(), "Envoyer à Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Une préparation d'envoi à Claude en cours (ou terminée, en attente d'un geste pour ouvrir Claude). */
data class ClaudeTask(
    val ficId: String,
    val title: String,
    val message: String,
    val done: Int = 0,
    val total: Int = 0,
    val ready: ClaudePrep.Ready? = null
)

/**
 * Préparation des fichiers pour Claude, indépendante de l'écran : elle continue si la fenêtre est fermée
 * ou si l'on quitte Ohara (service de premier plan avec notification). Quand Ohara n'est pas à l'écran à la fin,
 * Android interdit d'ouvrir une autre appli : une notification « Prêt à envoyer à Claude » attend alors un geste,
 * et l'envoi part aussi tout seul dès qu'on revient dans Ohara.
 */
object ClaudePrep {
    class Ready(val files: List<File>, val prompt: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _state = MutableStateFlow<ClaudeTask?>(null)
    val state: StateFlow<ClaudeTask?> = _state.asStateFlow()

    /** Ohara est-elle à l'écran ? (tenu à jour par l'activité principale) */
    @Volatile var visible = false
        private set

    fun start(context: Context, fic: Fic, range: Exporter.Range, prompt: String) {
        val app = context.applicationContext
        job?.cancel()
        Notify.cancelClaude(app)
        _state.value = ClaudeTask(fic.id, fic.title, "Préparation…")
        DownloadService.start(app)
        job = scope.launch {
            try {
                val (files, parts) = withContext(Dispatchers.IO) {
                    var lastRefresh = 0L
                    Exporter.claudeFiles(app, fic, range) { msg, d, t ->
                        _state.update { it?.copy(message = msg, done = d, total = t) }
                        // la notification est rafraîchie au plus trois fois par seconde
                        val now = System.currentTimeMillis()
                        if (now - lastRefresh > 330) { lastRefresh = now; DownloadService.refresh(app) }
                    }
                }
                // Le nombre de parties est connu maintenant : on le corrige dans la consigne.
                val p = prompt.replace(Regex("le texte en \\d+ parties?"), "le texte en $parts partie${if (parts > 1) "s" else ""}")
                Library.setShelf(fic.id, Shelves.CLAUDE, true)
                val ready = Ready(files, p)
                if (visible && !Locker.locked) {
                    _state.value = null
                    ClaudeSend.send(app, ready.files, ready.prompt)
                } else {
                    _state.update { it?.copy(message = "Prêt : touchez la notification pour ouvrir Claude.", ready = ready) }
                    Notify.claudeReady(app, fic.title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = null
                val msg = "Préparation impossible : ${e.message ?: "erreur inconnue"}"
                Toast.show(msg)
                if (!visible) Notify.claudeFailed(app, msg)
            } finally {
                DownloadService.refresh(app)
            }
        }
    }

    fun cancel(context: Context) {
        job?.cancel()
        job = null
        _state.value = null
        Notify.cancelClaude(context.applicationContext)
        DownloadService.refresh(context.applicationContext)
        Toast.show("Envoi à Claude annulé.")
    }

    /** Ohara revient à l'écran : si des fichiers attendent, on les envoie maintenant. */
    fun onVisible(context: Context) {
        visible = true
        val ready = _state.value?.ready ?: return
        _state.value = null
        Notify.cancelClaude(context.applicationContext)
        DownloadService.refresh(context.applicationContext)
        ClaudeSend.send(context, ready.files, ready.prompt)
    }

    fun markVisible() { visible = true }

    fun onHidden() { visible = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClaudeSheet(fic0: Fic, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fic = remember { Library.get(fic0.id) ?: Library.upsert(fic0) }
    val total = fic.totalChapters.coerceAtLeast(1)
    val kept = remember { (0 until total).count { Library.hasChapter(fic.id, it) } }
    // Par défaut : jusqu'au dernier chapitre lu (ou tout, si rien n'a été lu).
    val lastRead = if (fic.maxChapterRead >= 0) fic.maxChapterRead + 1 else total
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf(lastRead.coerceIn(1, total).toString()) }
    val range = run {
        val a = (from.toIntOrNull() ?: 1).coerceIn(1, total) - 1
        Exporter.Range(a, ((to.toIntOrNull() ?: total).coerceIn(a + 1, total)) - 1)
    }
    val prompts = remember(range) { Exporter.prompts(fic, 1, range) }
    var chosen by remember { mutableStateOf(Prefs.getString("claude_prompt", "continuer")) }
    var text by remember(chosen, range) { mutableStateOf(prompts.firstOrNull { it.id == chosen }?.text ?: prompts.first().text) }
    val task by ClaudePrep.state.collectAsState()
    val mine = task?.takeIf { it.ficId == fic.id }
    val other = task?.takeIf { it.ficId != fic.id }

    // La préparation de cette fic vient de se terminer (envoyée, annulée ou en erreur) : la fenêtre se ferme.
    var started by remember { mutableStateOf(mine != null) }
    LaunchedEffect(mine == null) {
        if (mine != null) started = true
        else if (started) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Envoyer à Claude", style = MaterialTheme.typography.titleLarge)
            if (mine != null) {
                Spacer(Modifier.height(16.dp))
                Text(mine.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (mine.total > 0 && mine.ready == null) {
                    LinearProgressIndicator(progress = { mine.done.toFloat() / mine.total }, modifier = Modifier.fillMaxWidth())
                } else if (mine.ready == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("Vous pouvez fermer cette fenêtre ou quitter Ohara : la préparation continue. Si Ohara n'est plus à l'écran à la fin, une notification vous permet d'ouvrir Claude d'un geste.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(onClick = { ClaudePrep.cancel(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(8.dp)); Text("Annuler")
                    }
                    Spacer(Modifier.width(12.dp))
                    if (mine.ready != null) Button(onClick = { ClaudePrep.onVisible(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir Claude")
                    } else Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Continuer en fond") }
                }
            } else {
                Text("La fiche, vos personnages, vos surlignages et le texte partent ensemble vers l'appli Claude, avec la consigne choisie.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(from, { from = it.filter(Char::isDigit) }, label = { Text("Du chapitre") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(to, { to = it.filter(Char::isDigit) }, label = { Text("au chapitre") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("$kept chapitre${if (kept > 1) "s" else ""} sur $total déjà sur le téléphone ; les autres seront téléchargés.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Text("Consigne", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    prompts.forEach { p -> FilterChip(chosen == p.id, { chosen = p.id; Prefs.putString("claude_prompt", p.id) }, { Text(p.label) }) }
                }
                OutlinedTextField(text, { text = it }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Modifiez ce qui est entre crochets avant d'envoyer.") })
                Spacer(Modifier.height(12.dp))
                if (other != null) Text("« ${other.title} » est déjà en préparation : un nouvel envoi la remplacera.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = {
                    started = true
                    ClaudePrep.start(context, fic, range, text)
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Envoyer à Claude")
                }
            }
        }
    }
}
