package com.muri.tripscout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muri.tripscout.model.Article
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

fun formatDate(date: LocalDate?): String = date?.format(SHORT_DATE) ?: "?"
fun formatLongDate(date: LocalDate): String = date.format(LONG_DATE)

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** Long text collapsed to a few lines with a "Show more" toggle. */
@Composable
fun ExpandableText(text: String, collapsedLines: Int = 4) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.lines().size > collapsedLines || text.length > collapsedLines * 60) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
        }
    }
}

@Composable
fun ArticleLink(article: Article) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(article.url) } }
            .padding(vertical = 6.dp),
    ) {
        Text(article.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        val meta = listOfNotNull(article.source, article.published).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall)
    }
}
