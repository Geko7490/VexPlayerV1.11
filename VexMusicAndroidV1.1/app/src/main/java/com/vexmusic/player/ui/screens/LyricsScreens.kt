package com.vexmusic.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vexmusic.player.domain.model.LyricsDisplayMode
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.ui.LyricsViewModel
import com.vexmusic.player.ui.MainViewModel
import com.vexmusic.player.ui.Routes
import com.vexmusic.player.ui.components.EmptyState
import com.vexmusic.player.ui.components.formatDuration
import com.vexmusic.player.ui.theme.VexCard
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary

@Composable
fun LyricsScreen(vm: MainViewModel, lyricsVm: LyricsViewModel, nav: NavHostController) {
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val progress by vm.playbackProgress.collectAsStateWithLifecycle()
    val state by lyricsVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val fileImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { lyricsVm.importLyricsFile(context, it) } }

    LaunchedEffect(track?.id) { track?.let { lyricsVm.loadForTrack(it) } }

    val parsed = state.parsed
    val activeIndex = if (parsed?.isSynced == true) lyricsVm.activeLineIndex(progress.positionMs) else -1

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = track?.title ?: "Lyrics",
            subtitle = state.saved?.let {
                "${it.providerName} · ${lyricsVm.syncTypeLabel(it.syncType)}" +
                    if (it.userEdited) " · edited by you" else ""
            } ?: "No lyrics selected yet",
            onBack = { nav.popBackStack() }
        ) {
            IconButton(onClick = { lyricsVm.openEditor(true) }) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = { nav.navigate(Routes.LYRICS_SOURCES) }) {
                Icon(Icons.Filled.Refresh, "Sources")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricsDisplayMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.displayMode == mode,
                    onClick = { lyricsVm.setDisplayMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                LyricsDisplayMode.ORIGINAL -> "Original"
                                LyricsDisplayMode.TRANSLATION -> "Translation"
                                LyricsDisplayMode.BOTH -> "Both"
                            }
                        )
                    }
                )
            }
            AssistChip(
                onClick = { nav.navigate(Routes.LYRICS_SOURCES) },
                label = { Text("Change source") }
            )
            AssistChip(
                onClick = { lyricsVm.translate(force = true) },
                label = { Text("Refresh translation") },
                leadingIcon = { Icon(Icons.Filled.Translate, null, Modifier.size(16.dp)) }
            )
        }

        if (parsed?.isSynced == true) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offset", style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
                TextButton(onClick = {
                    lyricsVm.setOffset((state.saved?.offsetMs ?: 0L) - 250)
                }) { Text("-0.25s") }
                Text(
                    "${(state.saved?.offsetMs ?: 0L) / 1000f}s",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    lyricsVm.setOffset((state.saved?.offsetMs ?: 0L) + 250)
                }) { Text("+0.25s") }
            }
        }

        if (state.translating) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = VexPurple)
                Spacer(Modifier.width(8.dp))
                Text("Translating…", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.translationError?.let {
            Text(
                "Translation unavailable: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VexPurple)
                    Spacer(Modifier.height(12.dp))
                    Text("Searching lyrics providers…", style = MaterialTheme.typography.bodySmall)
                }
            }

            parsed == null -> Column(Modifier.fillMaxSize()) {
                EmptyState(
                    "No lyrics selected",
                    state.message
                        ?: "VEX MUSIC never picks lyrics for you. Open the source list and choose the result you believe is correct.",
                    "Choose a source"
                ) { nav.navigate(Routes.LYRICS_SOURCES) }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { fileImport.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Import .lrc/.txt") }
                    OutlinedButton(
                        onClick = { lyricsVm.openEditor(true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Write lyrics") }
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
            ) {
                item {
                    if (!parsed.isSynced) {
                        Text(
                            "Plain text lyrics — not synchronized",
                            style = MaterialTheme.typography.labelSmall,
                            color = VexTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(parsed.lines.size) { index ->
                    val line = parsed.lines[index]
                    val isActive = index == activeIndex
                    val translation = state.translatedLines.getOrNull(index)
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = line.timeMs != null) {
                                line.timeMs?.let { vm.seekTo(it + (state.saved?.offsetMs ?: 0L)) }
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        if (state.displayMode != LyricsDisplayMode.TRANSLATION) {
                            Text(
                                line.text.ifBlank { "♪" },
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = if (isActive) 22.sp else 18.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isActive -> VexPurple
                                    parsed.isSynced && activeIndex >= 0 && index < activeIndex ->
                                        VexTextSecondary.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        if (state.displayMode != LyricsDisplayMode.ORIGINAL && !translation.isNullOrBlank()) {
                            Text(
                                translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) VexPurple.copy(alpha = 0.85f) else VexTextSecondary
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(160.dp)) }
            }
        }
    }

    if (state.editorOpen) {
        var text by remember { mutableStateOf(state.saved?.content ?: "") }
        AlertDialog(
            onDismissRequest = { lyricsVm.openEditor(false) },
            title = { Text("Edit lyrics") },
            text = {
                Column {
                    Text(
                        "LRC timestamps like [00:12.50] are kept and stay synchronized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { lyricsVm.saveEdited(text) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { lyricsVm.openEditor(false) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LyricsSourceScreen(lyricsVm: LyricsViewModel, nav: NavHostController) {
    val state by lyricsVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val outcome = state.outcome

    val fileImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { lyricsVm.importLyricsFile(context, it) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Choose lyrics source",
            state.track?.let { "${it.artist} — ${it.title}" } ?: "",
            onBack = { nav.popBackStack() }
        ) {
            IconButton(onClick = { lyricsVm.search() }) { Icon(Icons.Filled.Refresh, "Search again") }
        }

        state.saved?.let { saved ->
            Card(
                colors = CardDefaults.cardColors(containerColor = VexPurple.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Currently used", style = MaterialTheme.typography.labelSmall, color = VexPurple)
                    Text(
                        "${saved.providerName} · ${lyricsVm.syncTypeLabel(saved.syncType)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row {
                        TextButton(onClick = { lyricsVm.clearSelection() }) {
                            Text("Return to automatic search")
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { lyricsVm.search() }, modifier = Modifier.weight(1f)) {
                Text("Search again")
            }
            OutlinedButton(
                onClick = { fileImport.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("Import file") }
        }

        if (state.searching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VexPurple)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Querying ${lyricsVm.providers.size} providers…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            outcome?.failures?.takeIf { it.isNotEmpty() }?.let { failures ->
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "Provider problems",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        failures.forEach {
                            Text(
                                "${it.providerName}: ${it.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VexTextSecondary
                            )
                        }
                    }
                }
            }
            if (outcome?.emptyProviders?.isNotEmpty() == true) {
                item {
                    Text(
                        "No results from: ${outcome.emptyProviders.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            val results = outcome?.results.orEmpty()
            if (results.isEmpty()) {
                item {
                    EmptyState(
                        "No lyrics results",
                        "Nothing was returned for this track. You can import an .lrc/.txt file " +
                            "or write the lyrics yourself — VEX MUSIC will never invent lyrics.",
                        "Import .lrc/.txt"
                    ) { fileImport.launch(arrayOf("*/*")) }
                }
            }
            items(results.size) { index ->
                val result = results[index]
                val expanded = state.preview?.resultId == result.resultId &&
                    state.preview?.providerId == result.providerId
                Card(
                    colors = CardDefaults.cardColors(containerColor = VexCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                result.providerName,
                                style = MaterialTheme.typography.labelLarge,
                                color = VexPurple
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (result.syncType) {
                                    SyncType.WORD_SYNCED -> "Word-synced"
                                    SyncType.LINE_SYNCED -> "Synced LRC"
                                    SyncType.PLAIN -> "Plain text"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = VexTextSecondary
                            )
                            Spacer(Modifier.weight(1f))
                            result.matchScore?.let {
                                Text(
                                    "$it%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VexTextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(result.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildString {
                                append(result.artist)
                                result.album?.let { append(" · $it") }
                                result.durationMs?.let { append(" · ${formatDuration(it)}") }
                                result.language?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = VexTextSecondary
                        )
                        result.matchReason?.let {
                            Text(
                                "Match: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = VexTextSecondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (expanded) result.content.take(4000) else result.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = VexTextSecondary.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                lyricsVm.preview(if (expanded) null else result)
                            }) { Text(if (expanded) "Hide preview" else "Preview") }
                            Button(onClick = {
                                lyricsVm.select(result)
                                nav.popBackStack()
                            }) { Text("Use this") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}
