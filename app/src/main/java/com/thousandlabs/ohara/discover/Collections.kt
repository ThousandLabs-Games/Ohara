package com.thousandlabs.ohara.discover

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Shelves

/**
 * Collections construites toutes seules à partir de la bibliothèque :
 * par fandom et par type, et selon la vie de la fic (mises à jour récentes, en pause, longues…).
 * La plupart des fics ne sont pas terminées : aucune collection n'exige qu'elles le soient.
 */
object Collections {
    data class Coll(val id: String, val title: String, val subtitle: String, val ids: Set<String>) {
        val size get() = ids.size
    }

    private const val DAY = 86_400_000L

    fun build(fics: List<Fic>, now: Long = System.currentTimeMillis()): List<Coll> {
        if (fics.size < 3) return emptyList()
        val out = mutableListOf<Coll>()
        fun add(id: String, title: String, sub: String, pick: (Fic) -> Boolean, min: Int = 2) {
            val ids = fics.filter(pick).map { it.id }.toSet()
            if (ids.size >= min && ids.size < fics.size) out += Coll(id, title, sub, ids)
        }
        val alive = { f: Fic -> Shelves.ABANDONED !in f.shelves && !f.deletedFromSite }

        // Vie de la fic
        add("maj", "Mises à jour ce mois-ci", "Nouveaux chapitres depuis moins de 30 jours", { it.updated > now - 30 * DAY && alive(it) })
        add("prometteuses", "Actives et prometteuses", "Bon score Prometteuse et toujours vivantes",
            { alive(it) && Scoring.isPromising(Scoring.score(it, now)) && (it.status == FicStatus.COMPLETE || it.updated > now - 180 * DAY) })
        add("a_jour", "Presque à jour", "Plus que deux chapitres au plus avant la fin publiée",
            { it.totalChapters > 3 && it.maxChapterRead >= 0 && it.totalChapters - (it.maxChapterRead + 1) in 1..2 })
        add("rattraper", "À rattraper", "Au moins dix chapitres publiés que vous n'avez pas lus",
            { alive(it) && it.maxChapterRead >= 0 && it.totalChapters - (it.maxChapterRead + 1) >= 10 })
        add("longues", "Très longues", "Plus de 200 000 mots", { it.words > 200_000 })
        add("courtes", "Pour une soirée", "Moins de 40 000 mots", { it.words in 1..40_000 })
        add("pause", "En pause depuis plus d'un an", "Pas terminées, pas de nouvelle depuis un an",
            { it.status != FicStatus.COMPLETE && it.updated in 1..(now - 365 * DAY) })
        add("oubliees", "Pas rouvertes depuis longtemps", "Commencées, puis laissées plus de deux mois",
            { it.lastOpened in 1..(now - 60 * DAY) && Shelves.DONE !in it.shelves && alive(it) })
        add("terminees", "Terminées", "L'auteur a fini l'histoire", { it.status == FicStatus.COMPLETE })
        add("crossovers", "Crossovers", "Plusieurs univers mélangés", { it.crossover || it.fandoms.size > 1 })

        // Fandoms, puis fandom + type
        val byFandom = fics.filter { !it.crossover && it.fandoms.size == 1 }.groupBy { it.fandoms.first() }
            .filter { it.value.size >= 3 }.entries.sortedByDescending { it.value.size }.take(6)
        byFandom.forEach { (fandom, list) ->
            out += Coll("f:$fandom", fandom, "${list.size} fics de ce fandom", list.map { it.id }.toSet())
            list.flatMap { f -> Types.detect(f).map { it to f } }.groupBy({ it.first }, { it.second })
                .filter { it.value.size >= 2 && it.value.size < list.size }.entries.sortedByDescending { it.value.size }.take(2)
                .forEach { (type, fs) -> out += Coll("ft:$fandom:${type.name}", "$fandom · ${type.label}", "${fs.size} fics", fs.map { it.id }.toSet()) }
        }
        // Types tous fandoms confondus
        fics.flatMap { f -> Types.detect(f).map { it to f } }.groupBy({ it.first }, { it.second })
            .filter { it.value.size >= 3 }.entries.sortedByDescending { it.value.size }.take(4)
            .forEach { (type, fs) -> out += Coll("t:${type.name}", type.label, "Tous fandoms confondus", fs.map { it.id }.toSet()) }

        return out.distinctBy { it.ids to it.title }
    }
}
