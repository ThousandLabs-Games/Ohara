package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Shelves
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.LinkOpener
import com.thousandlabs.ohara.sources.Sources
import kotlinx.coroutines.launch

/**
 * Un lien de fic collé ou partagé : il s'ouvre dans le navigateur, exactement à cet endroit.
 * La bibliothèque se met à jour en lisant (voir [ReadTracker]).
 */
object LinkFlow {
    fun open(text: String, shelf: String = Shelves.READING, read: Boolean = true, download: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val url = Sources.extractUrl(text)
        if (url == null) { Toast.show("Aucun lien trouvé dans le texte."); onDone(false); return }
        if (read || Sources.resolve(url) == null) {
            Nav.openBrowser(url)
            onDone(true)
            return
        }
        // « Ajouter seulement » : lecture de la fiche en arrière-plan (peut échouer si le site bloque)
        AppScope.launch {
            runCatching { LinkOpener.open(url, shelf) }
                .onSuccess { o ->
                    Toast.show("« ${o.fic.title} » ajoutée." + if (download) " Téléchargement lancé." else "")
                    if (download) Downloads.enqueue(o.fic)
                    onDone(true)
                }
                .onFailure { e ->
                    Toast.show("Le site n'a pas répondu (${e.message ?: "erreur"}) : la page s'ouvre, utilisez le menu › « Ajouter à la bibliothèque ».")
                    Nav.openBrowser(url)
                    onDone(false)
                }
        }
    }
}
