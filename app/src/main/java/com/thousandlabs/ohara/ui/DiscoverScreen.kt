package com.thousandlabs.ohara.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.discover.Discover
import com.thousandlabs.ohara.discover.FandomIndex
import com.thousandlabs.ohara.discover.FicType
import com.thousandlabs.ohara.discover.Rec
import com.thousandlabs.ohara.discover.Search
import com.thousandlabs.ohara.discover.SearchQuery
import com.thousandlabs.ohara.discover.SearchSort
import com.thousandlabs.ohara.discover.SiteNote
import com.thousandlabs.ohara.discover.StatusFilter
import com.thousandlabs.ohara.discover.Types
import com.thousandlabs.ohara.sources.FanFictionNet
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverTab() {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { Nav.pop() }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                    title = { Text("Découvrir", style = MaterialTheme.typography.titleLarge.merge(SerifTitle)) },
                    actions = {
                        IconButton(onClick = { Nav.push(Screen.Hidden) }) { Icon(Icons.Outlined.VisibilityOff, "Fics masquées par le filtre") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Pour vous", "Nouveautés", "Recherche").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> ForYouPane()
                1 -> NewsPane()
                else -> SearchForm()
            }
        }
    }
}

@Composable
private fun NotesList(notes: List<SiteNote>) {
    notes.distinctBy { it.text }.forEach { n ->
        val u = n.verifyUrl
        if (u != null) NoteCard(n.text, icon = Icons.Outlined.ErrorOutline, action = {
            TextButton(onClick = { BrowserHolder.verifying = true; Nav.openBrowser(u) }) { Text("Ouvrir") }
        }) else NoteCard(n.text, icon = Icons.Outlined.ErrorOutline)
    }
}

/** Bouton « ajouter à la bibliothèque » sur un résultat. */
@Composable
fun QuickAdd(f: Fic) {
    val lib by Library.fics.collectAsState()
    if (lib.any { it.id == f.id }) return
    IconButton(onClick = { Library.upsert(f); Toast.show("« ${f.title} » ajoutée à « À lire ».") }) {
        Icon(Icons.Outlined.LibraryAdd, "Ajouter à la bibliothèque", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RecList(items: List<Rec>, header: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item { header() }
        items(items, key = { it.fic.id }) { r ->
            FicRow(r.fic, score = r.promising, types = Types.detect(r.fic), why = r.why.takeIf { it.isNotBlank() }?.let { "Pour vous : $it" },
                trailing = { QuickAdd(r.fic) }) { Nav.push(Screen.FicDetail(r.fic.id, r.fic)) }
            Divider()
        }
    }
}

@Composable
private fun ForYouPane() {
    val st by Discover.forYou.collectAsState()
    LaunchedEffect(Unit) { if (st.items.isEmpty() && !st.running && st.message == null) Discover.refreshForYou() }
    RecList(st.items) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choisies selon vos lectures", style = MaterialTheme.typography.titleMedium)
                    Text(if (st.updatedAt > 0) "Mises à jour ${Fmt.relative(st.updatedAt)} · filtre anti-déchets actif" else "Fandoms, personnages, types de fics et notes de votre bibliothèque",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (st.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { Discover.refreshForYou() }) { Icon(Icons.Outlined.Refresh, "Actualiser") }
            }
            if (st.running) Text(st.progress, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            st.message?.let { NoteCard(it, icon = Icons.Outlined.AutoAwesome) }
            NotesList(st.notes)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsPane() {
    val st by Discover.news.collectAsState()
    val lib by Library.fics.collectAsState()
    val pv by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") pv
    var pin by remember { mutableStateOf("") }
    val updated = lib.filter { it.newChapters > 0 || it.completedAlert }
    LaunchedEffect(Unit) { if (st.items.isEmpty() && !st.running && st.message == null) Discover.refreshNews() }
    RecList(st.items) {
        Column {
            if (updated.isNotEmpty()) {
                SectionTitle("Dans votre bibliothèque")
                updated.forEach { f -> FicRow(f, showProgress = true) { Nav.push(Screen.FicDetail(f.id)) }; Divider() }
            }
            SectionTitle("Fandoms épinglés")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Prefs.pinnedFandoms.forEach { fd ->
                    FilterChip(selected = true, onClick = { Prefs.pinnedFandoms = Prefs.pinnedFandoms - fd }, label = { Text(fd) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Retirer", Modifier.size(16.dp)) })
                }
            }
            FandomField(pin, { pin = it }, label = "Épingler un fandom", onPick = { fd ->
                if (fd.isNotBlank() && fd !in Prefs.pinnedFandoms) Prefs.pinnedFandoms = Prefs.pinnedFandoms + fd.trim()
                pin = ""
            })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nouvelles fics de vos fandoms", style = MaterialTheme.typography.titleMedium)
                    Text("Publiées ces 30 derniers jours · les prometteuses d'abord", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (st.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { Discover.refreshNews() }) { Icon(Icons.Outlined.Refresh, "Actualiser") }
            }
            if (st.running) Text(st.progress, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            st.message?.let { NoteCard(it, icon = Icons.Outlined.FiberNew) }
            NotesList(st.notes)
        }
    }
}

/** Champ de fandom avec propositions (bibliothèque puis index de FanFiction.net). */
@Composable
fun FandomField(value: String, onChange: (String) -> Unit, label: String = "Fandom", onPick: ((String) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val building by FandomIndex.building.collectAsState()
    val sugg = remember(value) { FandomIndex.suggest(value) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it); open = true }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (onPick != null) ({ IconButton(onClick = { onPick(value) }) { Icon(Icons.Outlined.PushPin, "Épingler") } }) else null,
            supportingText = if (building != null) { { Text(building ?: "") } } else null
        )
        DropdownMenu(expanded = open && sugg.isNotEmpty() && value.length >= 2, onDismissRequest = { open = false }) {
            sugg.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = { open = false; if (onPick != null) onPick(s) else onChange(s) })
            }
        }
    }
    LaunchedEffect(value) {
        if (value.length >= 3 && !FandomIndex.ready && building == null) AppScope.launch { runCatching { FandomIndex.ensure() } }
    }
}

private val WORD_MIN = listOf(0L to "Toutes", 10_000L to "10 k+", 50_000L to "50 k+", 100_000L to "100 k+", 200_000L to "200 k+", 500_000L to "500 k+")
private val WORD_MAX = listOf(0L to "Sans limite", 20_000L to "< 20 k", 60_000L to "< 60 k", 150_000L to "< 150 k", 400_000L to "< 400 k")
private val DAYS = listOf(0 to "Toujours", 7 to "1 semaine", 30 to "1 mois", 180 to "6 mois", 365 to "1 an", 730 to "2 ans")

object RecentSearches {
    fun list(): List<SearchQuery> = Prefs.getList("recent_q").mapNotNull { runCatching { SearchQuery.fromJson(JSONObject(it)) }.getOrNull() }
    fun add(q: SearchQuery) {
        val s = q.toJson().toString()
        Prefs.putList("recent_q", (listOf(s) + Prefs.getList("recent_q").filter { it != s }).take(10))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchForm() {
    val saved = remember { RecentSearches.list().firstOrNull() ?: SearchQuery() }
    var sites by rememberSaveable { mutableStateOf(saved.sites.map { it.code }.toSet()) }
    var keywords by rememberSaveable { mutableStateOf("") }
    var fandom by rememberSaveable { mutableStateOf("") }
    var chars by rememberSaveable { mutableStateOf("") }
    var include by rememberSaveable { mutableStateOf(setOf<String>()) }
    var exclude by rememberSaveable { mutableStateOf(setOf<String>()) }
    var genres by rememberSaveable { mutableStateOf(setOf<String>()) }
    var minW by rememberSaveable { mutableStateOf(0L) }
    var maxW by rememberSaveable { mutableStateOf(0L) }
    var minC by rememberSaveable { mutableStateOf("") }
    var maxC by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(StatusFilter.ANY) }
    var pubDays by rememberSaveable { mutableStateOf(0) }
    var updDays by rememberSaveable { mutableStateOf(0) }
    var year by rememberSaveable { mutableStateOf("") }
    var newOnly by rememberSaveable { mutableStateOf(false) }
    var promising by rememberSaveable { mutableStateOf(false) }
    var english by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(SearchSort.RELEVANCE) }
    var showGenres by rememberSaveable { mutableStateOf(false) }
    val recent = remember { RecentSearches.list() }

    fun build() = SearchQuery(
        sites = sites.map { Site.of(it) }.toSet(), keywords = keywords.trim(), fandom = fandom.trim(),
        characters = chars.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        include = include.mapNotNull { FicType.of(it) }.toSet(), exclude = exclude.mapNotNull { FicType.of(it) }.toSet(),
        genres = genres, minWords = minW, maxWords = maxW, minChapters = minC.toIntOrNull() ?: 0, maxChapters = maxC.toIntOrNull() ?: 0,
        status = status, publishedWithinDays = pubDays, updatedWithinDays = updDays, publishedFromYear = year.toIntOrNull() ?: 0,
        newOnly = newOnly, promisingOnly = promising, englishOnly = english, sort = sort
    )

    fun reset() {
        keywords = ""; fandom = ""; chars = ""; include = emptySet(); exclude = emptySet(); genres = emptySet()
        minW = 0; maxW = 0; minC = ""; maxC = ""; status = StatusFilter.ANY; pubDays = 0; updDays = 0; year = ""
        newOnly = false; promising = false; english = true; sort = SearchSort.RELEVANCE
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
        SectionTitle("Sites")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Site.FFN, Site.SB, Site.WN).forEach { s ->
                FilterChip(selected = s.code in sites, onClick = { sites = if (s.code in sites) sites - s.code else sites + s.code }, label = { Text(s.label) })
            }
        }
        SectionTitle("Quoi")
        OutlinedTextField(keywords, { keywords = it }, label = { Text("Mots-clés (titre, résumé)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        FandomField(fandom, { fandom = it })
        OutlinedTextField(chars, { chars = it }, label = { Text("Personnages (séparés par des virgules)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

        SectionTitle("Type de fic")
        Text("Une touche : inclure. Deux touches : exclure. Trois : sans importance.", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FicType.entries.forEach { t ->
                val inc = t.code in include
                val exc = t.code in exclude
                FilterChip(
                    selected = inc || exc,
                    onClick = {
                        when {
                            inc -> { include = include - t.code; exclude = exclude + t.code }
                            exc -> exclude = exclude - t.code
                            else -> include = include + t.code
                        }
                    },
                    label = { Text(if (exc) "Sans ${t.label}" else t.label) },
                    colors = if (exc) FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer) else FilterChipDefaults.filterChipColors()
                )
            }
        }
        TextButton(onClick = { showGenres = !showGenres }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(if (showGenres) "Masquer les genres" else "Genres (FanFiction.net)" + if (genres.isNotEmpty()) " · ${genres.size}" else "")
        }
        if (showGenres) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FanFictionNet.GENRES.forEach { g -> FilterChip(selected = g in genres, onClick = { genres = if (g in genres) genres - g else genres + g }, label = { Text(g) }) }
        }

        SectionTitle("Longueur")
        Text("Mots au minimum", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WORD_MIN.forEach { (v, l) -> FilterChip(selected = minW == v, onClick = { minW = v }, label = { Text(l) }) }
        }
        Text("Mots au maximum", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WORD_MAX.forEach { (v, l) -> FilterChip(selected = maxW == v, onClick = { maxW = v }, label = { Text(l) }) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedTextField(minC, { minC = it.filter(Char::isDigit) }, label = { Text("Chapitres min.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(maxC, { maxC = it.filter(Char::isDigit) }, label = { Text("Chapitres max.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }

        SectionTitle("Statut et dates")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusFilter.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
        }
        Text("Publiée depuis", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAYS.forEach { (v, l) -> FilterChip(selected = pubDays == v, onClick = { pubDays = v }, label = { Text(l) }) }
        }
        Text("Mise à jour depuis", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAYS.forEach { (v, l) -> FilterChip(selected = updDays == v, onClick = { updDays = v }, label = { Text(l) }) }
        }
        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Publiée à partir de l'année") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

        SectionTitle("Options")
        SwitchRow("Nouveautés seulement", "Publiées ces 30 derniers jours", newOnly) { newOnly = it }
        SwitchRow("Prometteuses seulement", "Score « Prometteuse » d'au moins ${com.thousandlabs.ohara.discover.Scoring.threshold}/100", promising) { promising = it }
        SwitchRow("En anglais seulement", "Filtre de langue de FanFiction.net", english) { english = it }
        SectionTitle("Tri")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchSort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val q = build()
                if (q.sites.isEmpty()) Toast.show("Choisissez au moins un site.")
                else { RecentSearches.add(q); Nav.push(Screen.Results(q)) }
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("Rechercher") }
            OutlinedButton(onClick = { reset() }) { Text("Effacer") }
        }
        if (recent.isNotEmpty()) {
            SectionTitle("Recherches récentes")
            recent.forEach { q -> SettingRow(q.describe(), q.sites.joinToString(" · ") { it.short }, Icons.Outlined.History, onClick = { Nav.push(Screen.Results(q)) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(query: SearchQuery) {
    val st by Search.state.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(query.sort) }
    LaunchedEffect(query) {
        if (st.query != query) {
            Search.start(query)
            com.thousandlabs.ohara.data.History.add(com.thousandlabs.ohara.data.History.Kind.SEARCH, query.describe(), "q:" + query.toJson())
        }
    }
    val list = remember(st.hits, sort) { Search.sort(st.hits, sort) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, st.loading) { if (nearEnd && !st.loading && st.canLoadMore && st.hits.isNotEmpty()) Search.loadMore() }

    Scaffold(topBar = {
        BackTopBar("Résultats", query.describe()) {
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Trier") }
                DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                    SearchSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false }) }
                }
            }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = listState, contentPadding = ListPadding) {
            item {
                NotesList(st.notes)
                if (st.hiddenCount > 0) NoteCard("${Fmt.plural(st.hiddenCount, "fic écartée", "fics écartées")} par le filtre anti-déchets.", icon = Icons.Outlined.VisibilityOff,
                    action = { TextButton(onClick = { Nav.push(Screen.Hidden) }) { Text("Voir") } })
            }
            items(list, key = { it.fic.id }) { h ->
                FicRow(h.fic, score = h.score, types = h.types, trailing = { QuickAdd(h.fic) }) { Nav.push(Screen.FicDetail(h.fic.id, h.fic)) }
                Divider()
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        st.loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Recherche en cours (une page à la fois, pour ménager les sites)…", style = MaterialTheme.typography.bodySmall)
                        }
                        st.canLoadMore -> OutlinedButton(onClick = { Search.loadMore() }) { Text("Plus de résultats") }
                        list.isEmpty() -> EmptyState(Icons.Outlined.Search, "Aucun résultat", "Élargissez la recherche : moins de filtres, un autre site, ou un autre nom de fandom.")
                        else -> Text("Fin des résultats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun SimilarScreen(base: Fic) {
    var items by remember { mutableStateOf<List<Rec>?>(null) }
    var notes by remember { mutableStateOf<List<SiteNote>>(emptyList()) }
    var progress by remember { mutableStateOf("Préparation…") }
    LaunchedEffect(base.id) {
        val (l, n) = Discover.similar(base) { progress = it }
        items = l; notes = n
    }
    Scaffold(topBar = { BackTopBar("Fics similaires", base.title) }) { pad ->
        val l = items
        if (l == null) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(progress, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Ohara cherche le même fandom, les mêmes personnages et le même type de fic, puis trie par ressemblance.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = ListPadding) {
            item { NotesList(notes) }
            if (l.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.TrendingUp, "Rien de proche trouvé", "Les sites n'ont pas répondu ou tout a été écarté par le filtre.") }
            items(l, key = { it.fic.id }) { r ->
                FicRow(r.fic, score = r.promising, types = Types.detect(r.fic), why = r.why, trailing = { QuickAdd(r.fic) }) {
                    Nav.push(Screen.FicDetail(r.fic.id, r.fic))
                }
                Divider()
            }
        }
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
