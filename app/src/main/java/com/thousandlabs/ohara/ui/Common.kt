package com.thousandlabs.ohara.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.FicStatus
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.discover.FicType
import com.thousandlabs.ohara.discover.Scoring
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- Mise en forme ----------

object Fmt {
    private val fr = Locale.FRANCE

    fun words(n: Long): String = when {
        n <= 0 -> "? mots"
        n < 1000 -> "$n mots"
        n < 1_000_000 -> String.format(fr, "%.1f k mots", n / 1000.0).replace(",0 k", " k")
        else -> String.format(fr, "%.2f M mots", n / 1_000_000.0)
    }

    fun number(n: Long): String = String.format(fr, "%,d", n).replace(' ', ' ').replace(' ', ' ')

    fun date(t: Long): String = if (t <= 0) "date inconnue" else SimpleDateFormat("d MMM yyyy", fr).format(Date(t))

    fun relative(t: Long, now: Long = System.currentTimeMillis()): String {
        if (t <= 0) return "date inconnue"
        val d = (now - t) / 1000
        return when {
            d < 60 -> "à l'instant"
            d < 3600 -> "il y a ${d / 60} min"
            d < 86400 -> "il y a ${d / 3600} h"
            d < 2 * 86400 -> "hier"
            d < 30 * 86400 -> "il y a ${d / 86400} jours"
            d < 365 * 86400 -> "il y a ${d / (30 * 86400)} mois"
            else -> { val y = d / (365 * 86400); "il y a $y an${if (y > 1) "s" else ""}" }
        }
    }

    fun duration(seconds: Long): String {
        if (seconds < 60) return "moins d'une minute"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h == 0L -> "$m min"
            h < 24 -> if (m > 0) "$h h $m min" else "$h h"
            else -> "${h / 24} j ${h % 24} h"
        }
    }

    fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n > 1) many else one}"

    fun bytes(b: Long): String = when {
        b < 1024 -> "$b o"
        b < 1024 * 1024 -> "${b / 1024} Ko"
        b < 1024L * 1024 * 1024 -> String.format(fr, "%.1f Mo", b / 1048576.0)
        else -> String.format(fr, "%.2f Go", b / 1073741824.0)
    }
}

// ---------- Éléments communs ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Petite étiquette arrondie (site, statut, badge). */
@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer, outlined: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .then(if (outlined) Modifier.border(1.dp, color, shape) else Modifier.background(color))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (outlined) color else textColor, maxLines = 1)
    }
}

@Composable
fun siteColor(site: Site): Color = when (site) {
    Site.FFN -> Color(0xFF2F5E9E)
    Site.SB -> Color(0xFF8A5A2B)
    Site.WN -> Color(0xFF7A3FA0)
    Site.OTHER -> Color(0xFF5B6B66)
}

@Composable
fun SitePill(site: Site) = Pill(site.short, color = siteColor(site), textColor = Color.White)

@Composable
fun ScorePill(score: Int) {
    val label = Scoring.label(score) ?: return
    Pill(label, color = Color(0xFFB7791F), textColor = Color.White)
}

private val COVER_COLORS = listOf(
    Color(0xFF1E5F74) to Color(0xFF133B5C), Color(0xFF7A3E48) to Color(0xFF4A1F2B), Color(0xFF2E6B4F) to Color(0xFF173F2C),
    Color(0xFF6B4E9B) to Color(0xFF3D2B63), Color(0xFF9B6A2E) to Color(0xFF5E3D15), Color(0xFF34558B) to Color(0xFF1D3157),
    Color(0xFF8A3B2E) to Color(0xFF52201A), Color(0xFF3E6F7A) to Color(0xFF223F47), Color(0xFF5A6B2E) to Color(0xFF333E17)
)

/** Couverture dessinée : couleur du fandom, initiales du titre, site. Les sites de fics n'ont presque jamais de vraie couverture. */
@Composable
fun CoverTile(fic: Fic, width: androidx.compose.ui.unit.Dp = 48.dp) {
    val key = fic.fandoms.firstOrNull() ?: fic.title
    val (c1, c2) = COVER_COLORS[(key.lowercase().hashCode() and 0x7fffffff) % COVER_COLORS.size]
    val initials = fic.title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it.lowercase() !in setOf("the", "a", "of", "an", "and", "le", "la", "les", "de", "in") }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    val h = width * 1.42f
    Box(
        Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c1, c2)))
    ) {
        Text(initials, modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.92f),
            fontSize = (width.value * 0.36f).sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        Text(fic.site.short, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp), color = Color.White.copy(alpha = 0.7f),
            fontSize = (width.value * 0.17f).sp, fontWeight = FontWeight.Medium)
        Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(h).background(Color.Black.copy(alpha = 0.18f)))
    }
}

/** Une fic dans une liste (bibliothèque, recherche, propositions). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FicRow(
    fic: Fic,
    modifier: Modifier = Modifier,
    score: Int? = null,
    types: Set<FicType> = emptySet(),
    why: String? = null,
    showProgress: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CoverTile(fic)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(fic.title.ifBlank { "Sans titre" }, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val by = listOfNotNull(fic.author.takeIf { it.isNotBlank() }, fic.fandomLabel.takeIf { fic.fandoms.isNotEmpty() }).joinToString(" · ")
            if (by.isNotEmpty()) Text(by, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SitePill(fic.site)
                if (score != null) ScorePill(score)
                if (fic.newChapters > 0) Pill(Fmt.plural(fic.newChapters, "nouveau chapitre", "nouveaux chapitres"), color = cs.primary, textColor = cs.onPrimary)
                if (fic.completedAlert) Pill("Enfin terminée", color = Color(0xFF2E7D32), textColor = Color.White)
                if (fic.deletedFromSite) Pill("Supprimée du site", color = cs.error, outlined = true)
                else if (Library.contains(fic.id) && Scoring.isInactive(fic)) Pill("Inactive", color = cs.outline, outlined = true)
                if (fic.status == FicStatus.COMPLETE && !fic.completedAlert) Pill("Terminée", outlined = true, color = cs.secondary)
                types.take(3).forEach { Pill(it.label, outlined = true, color = cs.tertiary) }
            }
            Spacer(Modifier.height(5.dp))
            val stats = buildList {
                add(Fmt.words(fic.words))
                if (fic.totalChapters > 0) add(Fmt.plural(fic.totalChapters, "chapitre"))
                if (fic.favs > 0) add("${Fmt.number(fic.favs.toLong())} favoris")
                else if (fic.views > 0) add("${Fmt.number(fic.views.toLong())} vues")
                if (fic.updated > 0) add("màj ${Fmt.relative(fic.updated)}")
            }.joinToString(" · ")
            Text(stats, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            if (why != null && why.isNotBlank()) {
                Text(why, style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!showProgress && fic.summary.isNotBlank() && !Library.contains(fic.id)) {
                Spacer(Modifier.height(4.dp))
                Text(fic.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = cs.onSurface.copy(alpha = 0.85f))
            }
            if (showProgress && fic.totalChapters > 0) {
                Spacer(Modifier.height(6.dp))
                val read = (fic.maxChapterRead + 1).coerceIn(0, fic.totalChapters)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { read.toFloat() / fic.totalChapters },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = cs.primary, trackColor = cs.surfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Ch. ${fic.progressChapter + 1}/${fic.totalChapters}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailingText, style = MaterialTheme.typography.labelSmall, color = cs.outline)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(Locale.FRANCE), modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        else if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, icon, onClick = { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange) }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, label: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/** Message centré quand une liste est vide. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Carte d'information discrète (avertissement d'un site, astuce). */
@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

val ListPadding = PaddingValues(bottom = 96.dp)

fun Float.pct(): String = "${(this * 100).roundToInt()} %"
