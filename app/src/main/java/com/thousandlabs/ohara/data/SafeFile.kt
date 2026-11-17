package com.thousandlabs.ohara.data

import java.io.File

/** Écriture sûre : le fichier est d'abord écrit à côté, puis remplace l'ancien d'un coup (rien n'est perdu si l'appli s'arrête en plein milieu). */
object SafeFile {
    fun write(f: File, text: String) {
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
        }
    }
}
