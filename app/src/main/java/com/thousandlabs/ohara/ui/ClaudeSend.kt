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
import kotlinx.coroutines.Dispatchers
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
    var busy by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = { if (busy == null) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Envoyer à Claude", style = MaterialTheme.typography.titleLarge)
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
            if (busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
                Text(busy!!, style = MaterialTheme.typography.bodyMedium)
            } else Button(onClick = {
                busy = "Préparation…"
                val prompt = text
                AppScope.launch {
                    runCatching { withContext(Dispatchers.IO) { Exporter.claudeFiles(context.applicationContext, fic, range) { busy = it } } }
                        .onSuccess { (files, parts) ->
                            busy = null
                            // Le nombre de parties est connu maintenant : on le corrige dans la consigne.
                            val p = prompt.replace(Regex("le texte en \\d+ parties?"), "le texte en $parts partie${if (parts > 1) "s" else ""}")
                            Library.setShelf(fic.id, Shelves.CLAUDE, true)
                            ClaudeSend.send(context, files, p)
                            onDismiss()
                        }
                        .onFailure { busy = null; Toast.show("Préparation impossible : ${it.message}") }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Envoyer à Claude")
            }
        }
    }
}
