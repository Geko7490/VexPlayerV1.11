package com.vexmusic.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vexmusic.player.AppContainer
import com.vexmusic.player.audio.EqualizerController
import com.vexmusic.player.domain.model.LyricsDisplayMode
import com.vexmusic.player.player.service.PlaybackService
import com.vexmusic.player.ui.MainViewModel
import com.vexmusic.player.ui.Routes
import com.vexmusic.player.ui.components.VexCardBox
import com.vexmusic.player.ui.components.VisualizerMode
import com.vexmusic.player.ui.components.formatBytes
import com.vexmusic.player.ui.navigateSingle
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary
import com.vexmusic.player.lyrics.translation.Languages
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EqualizerScreen(container: AppContainer, nav: NavHostController) {
    val capabilities by container.equalizerController.capabilities.collectAsStateWithLifecycle()
    val levels by container.equalizerController.levels.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initial = null)
    val sessionId by PlaybackService.audioSessionIdFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var bassStrength by remember(settings?.bassBoost) {
        mutableStateOf(settings?.bassBoost ?: 0)
    }
    var virtualizerStrength by remember(settings?.virtualizer) {
        mutableStateOf(settings?.virtualizer ?: 0)
    }

    LaunchedEffect(sessionId) {
        if (sessionId != 0) container.equalizerController.attach(sessionId)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            "Equalizer",
            if (capabilities.supported) "${capabilities.bands.size} bands · device AudioEffect"
            else "Not available on this device",
            onBack = { nav.popBackStack() }
        )

        if (!capabilities.supported) {
            VexCardBox(Modifier.padding(12.dp)) {
                Column {
                    Text("Equalizer unavailable", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        capabilities.reason
                            ?: "This Android device/ROM does not expose the AudioEffect equalizer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Start playing a track so VEX MUSIC can attach to the audio session, then reopen this screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary
                    )
                }
            }
            return@Column
        }

        val enabled = settings?.equalizerEnabled ?: false
        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            SettingRow("Enable equalizer", "Applies to the active playback session") {
                Switch(checked = enabled, onCheckedChange = { value ->
                    scope.launch {
                        container.settingsRepository.setEqualizerEnabled(value)
                        container.equalizerController.setEnabled(value)
                    }
                })
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqualizerController.presets.keys.forEach { preset ->
                FilterChip(
                    selected = settings?.equalizerPreset == preset,
                    onClick = {
                        val values = EqualizerController.levelsFor(
                            preset, capabilities.bands.size,
                            capabilities.minLevelMb, capabilities.maxLevelMb
                        )
                        container.equalizerController.applyLevels(values)
                        container.equalizerController.setEnabled(true)
                        scope.launch {
                            container.settingsRepository.setEqualizerPreset(preset)
                            container.settingsRepository.setEqualizerBands(values)
                            container.settingsRepository.setEqualizerEnabled(true)
                        }
                    },
                    label = { Text(preset) }
                )
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                capabilities.bands.forEachIndexed { index, band ->
                    val value = levels.getOrNull(index)?.toFloat() ?: 0f
                    Text(
                        "${band.centerFreqHz} Hz · ${"%.1f".format(value / 100)} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary
                    )
                    Slider(
                        value = value,
                        valueRange = capabilities.minLevelMb.toFloat()..capabilities.maxLevelMb.toFloat(),
                        onValueChange = {
                            container.equalizerController.setBandLevel(index, it.toInt())
                        },
                        onValueChangeFinished = {
                            scope.launch {
                                container.settingsRepository.setEqualizerBands(
                                    container.equalizerController.levels.value
                                )
                                container.settingsRepository.setEqualizerPreset("Custom")
                            }
                        }
                    )
                }
                if (capabilities.bassBoostSupported) {
                    Text("Bass boost", style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
                    Slider(
                        value = bassStrength.toFloat(),
                        valueRange = 0f..1000f,
                        onValueChange = {
                            bassStrength = it.toInt()
                            container.equalizerController.setBassBoost(bassStrength)
                        },
                        onValueChangeFinished = {
                            scope.launch { container.settingsRepository.setBassBoost(bassStrength) }
                        }
                    )
                }
                if (capabilities.virtualizerSupported) {
                    Text("Virtualizer", style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
                    Slider(
                        value = virtualizerStrength.toFloat(),
                        valueRange = 0f..1000f,
                        onValueChange = {
                            virtualizerStrength = it.toInt()
                            container.equalizerController.setVirtualizer(virtualizerStrength)
                        },
                        onValueChangeFinished = {
                            scope.launch { container.settingsRepository.setVirtualizer(virtualizerStrength) }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val flat = EqualizerController.levelsFor(
                        "Flat", capabilities.bands.size,
                        capabilities.minLevelMb, capabilities.maxLevelMb
                    )
                    container.equalizerController.applyLevels(flat)
                    scope.launch {
                        container.settingsRepository.setEqualizerBands(flat)
                        container.settingsRepository.setEqualizerPreset("Flat")
                    }
                }) { Text("Reset to flat") }
            }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
fun StorageScreen(vm: MainViewModel, nav: NavHostController) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshUsage() }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBackup(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Storage", "Everything stays on this device", onBack = { nav.popBackStack() })

        VexCardBox(Modifier.padding(12.dp)) {
            Column {
                StorageRow("Imported songs", "${usage.trackCount}")
                StorageRow("Music (referenced files)", formatBytes(usage.musicBytes))
                StorageRow("Artwork cache", formatBytes(usage.artworkBytes))
                StorageRow("Lyrics cache", "${usage.lyricsCount} · ${formatBytes(usage.lyricsBytes)}")
                StorageRow(
                    "Translation cache",
                    "${usage.translationCount} · ${formatBytes(usage.translationBytes)}"
                )
                StorageRow("Playlists", "${usage.playlistCount}")
                StorageRow("History entries", "${usage.historyCount}")
                StorageRow("Database file", formatBytes(usage.databaseBytes))
                StorageRow("Free space on device", formatBytes(usage.freeDeviceBytes))
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp)) {
            Column {
                Text("Maintenance", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearLyricsCache() }) { Text("Clear lyrics") }
                    OutlinedButton(onClick = { vm.clearTranslationCache() }) { Text("Clear translations") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear history") }
                    OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all data") }
                }
            }
        }

        VexCardBox(Modifier.padding(12.dp)) {
            Column {
                Text("Backup & restore", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Versioned JSON backup (v1): playlists, favorites, lyrics, translations, " +
                        "history, metadata and selected lyrics sources. Audio files are not included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exporter.launch("vex-music-backup.json") }) { Text("Export") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) {
                        Text("Restore")
                    }
                }
            }
        }
        Spacer(Modifier.height(140.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all VEX MUSIC data?") },
            text = {
                Text(
                    "Library entries, playlists, lyrics, translations, history and settings will be " +
                        "deleted. Your actual audio files on the device are never touched."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearAllData(); confirmClear = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VexPurple)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, container: AppContainer, nav: NavHostController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository
    val context = LocalContext.current
    var visualizerPermissionDenied by remember { mutableStateOf(false) }
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        visualizerPermissionDenied = !granted
        scope.launch { repo.setVisualizerEnabled(granted) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", "VEX MUSIC 1.0.0 · debug build")

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                Text("Interface", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingRow("Language", "App text language") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "System", "en" to "EN", "tr" to "TR").forEach { (code, label) ->
                            FilterChip(
                                selected = settings.language == code,
                                onClick = { scope.launch { repo.setLanguage(code) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Text(
                    "The UI ships with English and Turkish string resources; the system language is " +
                        "used by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
                Divider(Modifier.padding(vertical = 8.dp))
                Text("Theme: VEX Purple dark (fixed in this build)",
                    style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                Text("Lyrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingRow(
                    "Automatic provider search",
                    "Search when a song without a saved selection opens. Results are never auto-applied."
                ) {
                    Switch(
                        checked = settings.autoLyricsSearch,
                        onCheckedChange = { scope.launch { repo.setAutoLyricsSearch(it) } }
                    )
                }
                Divider(Modifier.padding(vertical = 6.dp))
                Text("Providers", style = MaterialTheme.typography.titleSmall)
                container.providers.forEach { provider ->
                    val enabled = settings.enabledProviders.contains(provider.id)
                    SettingRow(
                        provider.displayName,
                        if (provider.requiresNetwork) "Online provider" else "Offline provider"
                    ) {
                        Switch(checked = enabled, onCheckedChange = { checked ->
                            scope.launch {
                                val next = settings.enabledProviders.toMutableSet()
                                if (checked) next += provider.id else next -= provider.id
                                repo.setEnabledProviders(next)
                            }
                        })
                    }
                }
                Text(
                    "Paxsenix Lyrically is off by default: its search endpoints currently return " +
                        "HTTP 403 upstream. See README for details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
                Divider(Modifier.padding(vertical = 6.dp))
                Text("Default display mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LyricsDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.lyricsDisplayMode == mode,
                            onClick = { scope.launch { repo.setDisplayMode(mode) } },
                            label = { Text(mode.name.lowercase()) }
                        )
                    }
                }
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                Text("Translation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Provider: MyMemory. When enabled, lyric text is sent to this online provider " +
                        "over HTTPS; audio files are never sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
                SettingRow("Enable translation") {
                    Switch(
                        checked = settings.translationEnabled,
                        onCheckedChange = { scope.launch { repo.setTranslationEnabled(it) } }
                    )
                }
                Text("Target language", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Languages.targets.forEach { lang ->
                        FilterChip(
                            selected = settings.translationTargetLanguage == lang.code,
                            onClick = { scope.launch { repo.setTranslationLanguage(lang.code) } },
                            label = { Text(lang.nativeName) }
                        )
                    }
                }
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                Text("Playback & audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingRow("Resume last position", "Continue tracks where you left off") {
                    Switch(
                        checked = settings.resumeOnStart,
                        onCheckedChange = { scope.launch { repo.setResumeOnStart(it) } }
                    )
                }
                SettingRow(
                    "Audio visualizer",
                    when {
                        visualizerPermissionDenied -> "Microphone permission was denied; the visualizer remains off."
                        else -> "Uses the device Visualizer effect. Microphone access is requested only when enabled."
                    }
                ) {
                    Switch(
                        checked = settings.visualizerEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                visualizerPermissionDenied = false
                                scope.launch { repo.setVisualizerEnabled(false) }
                            } else if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                scope.launch { repo.setVisualizerEnabled(true) }
                            } else {
                                visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VisualizerMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.visualizerMode == mode.name,
                            onClick = { scope.launch { repo.setVisualizerMode(mode.name) } },
                            label = { Text(mode.name.lowercase()) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigateSingle(Routes.EQUALIZER) }) { Text("Equalizer") }
                    OutlinedButton(onClick = { nav.navigateSingle(Routes.STORAGE) }) { Text("Storage") }
                }
            }
        }

        VexCardBox(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column {
                Text("Privacy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Audio files never leave your device. Only the song title, artist, album and " +
                        "duration are sent to the lyrics providers you enable, over HTTPS. " +
                        "No analytics or tracking is built into this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}
