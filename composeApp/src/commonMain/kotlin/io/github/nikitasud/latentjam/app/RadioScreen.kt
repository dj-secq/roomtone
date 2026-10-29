/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nikitasud.latentjam.app.generated.resources.Res
import io.github.nikitasud.latentjam.app.generated.resources.cd_clear_query
import io.github.nikitasud.latentjam.app.generated.resources.count_tracks
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_balanced
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_body
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_free
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_high
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_low
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_artist_variety_max
import io.github.nikitasud.latentjam.app.generated.resources.intelligence_queue_length
import io.github.nikitasud.latentjam.app.generated.resources.radio_exclusions
import io.github.nikitasud.latentjam.app.generated.resources.radio_feedback_body
import io.github.nikitasud.latentjam.app.generated.resources.radio_filter_decade
import io.github.nikitasud.latentjam.app.generated.resources.radio_filter_folder
import io.github.nikitasud.latentjam.app.generated.resources.radio_filter_instrumental
import io.github.nikitasud.latentjam.app.generated.resources.radio_find_song
import io.github.nikitasud.latentjam.app.generated.resources.radio_for_this_listen
import io.github.nikitasud.latentjam.app.generated.resources.radio_keep_default
import io.github.nikitasud.latentjam.app.generated.resources.radio_more_like_this
import io.github.nikitasud.latentjam.app.generated.resources.radio_no_matches
import io.github.nikitasud.latentjam.app.generated.resources.radio_no_stations
import io.github.nikitasud.latentjam.app.generated.resources.radio_not_for_now
import io.github.nikitasud.latentjam.app.generated.resources.radio_play_this
import io.github.nikitasud.latentjam.app.generated.resources.radio_remove_station
import io.github.nikitasud.latentjam.app.generated.resources.radio_save_queue
import io.github.nikitasud.latentjam.app.generated.resources.radio_save_station
import io.github.nikitasud.latentjam.app.generated.resources.radio_smart_off
import io.github.nikitasud.latentjam.app.generated.resources.radio_station_missing
import io.github.nikitasud.latentjam.app.generated.resources.radio_station_name
import io.github.nikitasud.latentjam.app.generated.resources.radio_stations
import io.github.nikitasud.latentjam.app.generated.resources.radio_title
import io.github.nikitasud.latentjam.app.generated.resources.radio_too_repetitive
import io.github.nikitasud.latentjam.app.generated.resources.radio_travel
import io.github.nikitasud.latentjam.app.generated.resources.radio_travel_none
import io.github.nikitasud.latentjam.app.generated.resources.radio_upcoming
import io.github.nikitasud.latentjam.app.generated.resources.radio_upcoming_empty
import io.github.nikitasud.latentjam.app.generated.resources.radio_using_default
import io.github.nikitasud.latentjam.app.generated.resources.radio_while_playing
import io.github.nikitasud.latentjam.app.generated.resources.track_unknown_artist
import io.github.nikitasud.latentjam.app.generated.resources.track_untitled
import io.github.nikitasud.latentjam.playback.PlaybackController
import io.github.nikitasud.latentjam.playback.ShuffleMode
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The mix that is playing: the song it started from, what comes next, and the controls that
 * steer this listen. SMART itself is unchanged. Stay close and Explore stay off until measured.
 */
@Composable
internal fun RadioScreen(
    playback: PlaybackController,
    tracks: List<TrackDescriptor>,
    savedVariety: Int,
    listen: RadioListen?,
    stations: List<RadioStation>,
    onSessionVariety: (Int) -> Unit,
    onKeepDefault: () -> Unit,
    onMoreLikeThis: () -> Unit,
    onTooRepetitive: () -> Unit,
    onNotForNow: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onStartSmart: (TrackDescriptor) -> Unit,
    onSaveQueue: () -> Unit,
    onExclusions: () -> Unit,
    onSaveStation: (String) -> Unit,
    onStartStation: (RadioStation) -> Unit,
    onRemoveStation: (String) -> Unit,
    onFilter: (RadioFilter) -> Unit,
    onTravel: (TrackDescriptor) -> Unit,
    onClearTravelNote: () -> Unit,
    travelUnplotted: Boolean,
    instrumentalAvailable: Boolean,
    smartQueueLength: Int,
    onSmartQueueLength: (Int) -> Unit,
) {
    val now by remember(playback) {
        playback.state.map { state ->
            RadioNow(state.shuffleMode, state.track, state.queue, state.queueIndex)
        }.distinctUntilChanged()
    }.collectAsState(
        playback.state.value.let { state ->
            RadioNow(state.shuffleMode, state.track, state.queue, state.queueIndex)
        },
    )
    val smartOn = now.mode == ShuffleMode.SMART
    val byId = remember(tracks) { tracks.associateBy { it.id.value } }
    val seed = listen?.seedTrackId?.let { byId[it] } ?: now.queue.firstOrNull() ?: now.track
    val upcoming = remember(now) {
        if (now.index !in now.queue.indices) emptyList()
        else now.queue.drop(now.index + 1).take(UPCOMING_LIMIT)
    }
    val level = sanitizeArtistVariety(if (smartOn) listen?.artistVariety ?: savedVariety else savedVariety)
    val usingDefault = !smartOn || listen?.artistVariety == null || listen.artistVariety == savedVariety
    var query by remember { mutableStateOf("") }
    var pickingDestination by remember { mutableStateOf(false) }
    var stationName by remember(listen?.stationId, listen?.stationName) {
        mutableStateOf(listen?.stationName.orEmpty())
    }
    val matches = remember(tracks, query) {
        val needle = query.trim()
        if (needle.isEmpty()) emptyList()
        else tracks.filter { track ->
            track.title?.contains(needle, ignoreCase = true) == true ||
                track.artist?.contains(needle, ignoreCase = true) == true
        }.take(MATCH_LIMIT)
    }
    val varietyNames = listOf(
        Res.string.intelligence_artist_variety_free,
        Res.string.intelligence_artist_variety_low,
        Res.string.intelligence_artist_variety_balanced,
        Res.string.intelligence_artist_variety_high,
        Res.string.intelligence_artist_variety_max,
    )
    val canSave = (listen?.seedTrackId ?: now.track?.id?.value) != null && stationName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(Res.string.radio_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
        )
        if (!smartOn) {
            Text(
                text = stringResource(Res.string.radio_smart_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            seed?.let { SeedBlock(it, stationName = null) }
            now.track?.let { track ->
                FilledTonalButton(
                    onClick = { onStartSmart(track) },
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.radio_play_this))
                }
            }
        } else {
            seed?.let { SeedBlock(it, listen?.stationName) }
            RadioCard(stringResource(Res.string.radio_upcoming)) {
                if (upcoming.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.radio_upcoming_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    upcoming.forEachIndexed { offset, track ->
                        val index = now.index + 1 + offset
                        TrackRow(track, onClick = { onPlayAt(index) })
                    }
                }
            }
            RadioCard(stringResource(Res.string.radio_for_this_listen)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.intelligence_artist_variety),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(
                                if (usingDefault) Res.string.radio_using_default
                                else Res.string.intelligence_artist_variety_body,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(varietyNames[level]),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppSlider(
                    value = level.toFloat(),
                    onValueChange = { onSessionVariety(it.roundToInt()) },
                    valueRange = 0f..ARTIST_VARIETY_PENALTIES.lastIndex.toFloat(),
                    steps = ARTIST_VARIETY_PENALTIES.size - 2,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (!usingDefault) {
                    TextButton(
                        onClick = onKeepDefault,
                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.radio_keep_default))
                    }
                }
                FilterChips(
                    seed = listen?.seedTrackId?.let { byId[it] },
                    filter = listen?.filter ?: RadioFilter.OFF,
                    instrumentalAvailable = instrumentalAvailable,
                    onFilter = onFilter,
                )
            }
            RadioCard(stringResource(Res.string.intelligence_queue_length)) {
                QueueLengthPicker(
                    selected = smartQueueLength,
                    onSelect = onSmartQueueLength,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            RadioCard(stringResource(Res.string.radio_while_playing)) {
                Text(
                    text = stringResource(Res.string.radio_feedback_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                IconAction(LatentJamMark, stringResource(Res.string.radio_more_like_this), onMoreLikeThis)
                IconAction(
                    icon = Icons.Rounded.Shuffle,
                    label = stringResource(Res.string.radio_too_repetitive),
                    onClick = onTooRepetitive,
                    enabled = level < ARTIST_VARIETY_PENALTIES.lastIndex,
                )
                IconAction(Icons.Rounded.Close, stringResource(Res.string.radio_not_for_now), onNotForNow)
            }
        }
        RadioCard(stringResource(Res.string.radio_stations)) {
            if (stations.isEmpty()) {
                Text(
                    text = stringResource(Res.string.radio_no_stations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            val removeStation = stringResource(Res.string.radio_remove_station)
            stations.forEach { station ->
                val seedHere = byId[station.seedTrackId]
                TrackRow(
                    track = seedHere,
                    title = bidiWrap(station.name),
                    subtitle = if (seedHere == null) {
                        stringResource(Res.string.radio_station_missing)
                    } else {
                        "${seedHere.titleLine()} · ${seedHere.artistLine()}"
                    },
                    artworkUri = seedHere?.artworkUri,
                    onClick = if (seedHere == null) null else ({ onStartStation(station) }),
                    onRemove = { onRemoveStation(station.id) },
                    removeLabel = removeStation,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioField(
                    value = stationName,
                    onValueChange = { stationName = it.take(MAX_STATION_NAME_CHARS) },
                    placeholder = stringResource(Res.string.radio_station_name),
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                )
                FilledTonalButton(
                    onClick = { onSaveStation(stationName) },
                    enabled = canSave,
                    modifier = Modifier.padding(end = 12.dp).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.radio_save_station))
                }
            }
        }
        if (now.track != null) {
            IconAction(
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                label = stringResource(Res.string.radio_travel),
                onClick = {
                    pickingDestination = !pickingDestination
                    if (pickingDestination) onClearTravelNote()
                },
                selected = pickingDestination,
            )
            if (travelUnplotted) {
                Text(
                    text = stringResource(Res.string.radio_travel_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        RadioCard(
            if (pickingDestination) stringResource(Res.string.radio_travel) else stringResource(Res.string.radio_find_song),
        ) {
            RadioField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(
                    if (pickingDestination) Res.string.radio_travel else Res.string.radio_find_song,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (query.isNotBlank() && matches.isEmpty()) {
                Text(
                    text = stringResource(Res.string.radio_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            matches.forEach { track ->
                TrackRow(
                    track,
                    onClick = {
                        if (pickingDestination) {
                            pickingDestination = false
                            onTravel(track)
                        } else {
                            onStartSmart(track)
                        }
                    },
                )
            }
        }
        TextButton(
            onClick = onExclusions,
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(Res.string.radio_exclusions),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (now.queue.isNotEmpty()) {
            TextButton(
                onClick = onSaveQueue,
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(Res.string.radio_save_queue),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SeedBlock(track: TrackDescriptor, stationName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Artwork(uri = track.artworkUri, size = 92.dp, cornerRadius = 16.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.titleLine(),
                style = MaterialTheme.typography.titleMedium.metadata(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!stationName.isNullOrBlank()) {
                Text(
                    text = bidiWrap(stationName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RadioCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp.corner()),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackDescriptor?,
    onClick: (() -> Unit)?,
    title: String = track?.titleLine().orEmpty(),
    subtitle: String = track?.artistLine().orEmpty(),
    artworkUri: String? = track?.artworkUri,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(uri = artworkUri, size = 48.dp, cornerRadius = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.metadata(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = removeLabel)
            }
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun FilterChips(
    seed: TrackDescriptor?,
    filter: RadioFilter,
    instrumentalAvailable: Boolean,
    onFilter: (RadioFilter) -> Unit,
) {
    val folderOn = filter == RadioFilter.FOLDER
    val decadeOn = filter == RadioFilter.DECADE
    val instrumentalOn = filter == RadioFilter.INSTRUMENTAL
    val showFolder = radioChipVisible(folderKey(seed) != null, folderOn)
    val showDecade = radioChipVisible(decadeStart(seed) != null, decadeOn)
    val showInstrumental = radioChipVisible(instrumentalAvailable, instrumentalOn)
    if (!showFolder && !showDecade && !showInstrumental) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showFolder) {
            AppChoiceChip(
                selected = folderOn,
                onClick = { onFilter(if (folderOn) RadioFilter.OFF else RadioFilter.FOLDER) },
                label = stringResource(Res.string.radio_filter_folder),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (showDecade) {
            AppChoiceChip(
                selected = decadeOn,
                onClick = { onFilter(if (decadeOn) RadioFilter.OFF else RadioFilter.DECADE) },
                label = stringResource(Res.string.radio_filter_decade),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (showInstrumental) {
            AppChoiceChip(
                selected = instrumentalOn,
                onClick = { onFilter(if (instrumentalOn) RadioFilter.OFF else RadioFilter.INSTRUMENTAL) },
                label = stringResource(Res.string.radio_filter_instrumental),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun QueueLengthPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp.corner()))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SMART_QUEUE_LENGTH_OPTIONS.forEach { length ->
                val chosen = length == selected
                val label = pluralStringResource(Res.plurals.count_tracks, length, length)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp.corner()))
                        .background(
                            if (chosen) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .selectable(selected = chosen, role = Role.RadioButton) { onSelect(length) }
                        .semantics { contentDescription = label }
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = length.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = if (chosen) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = color).metadata(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp.corner()))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(Res.string.cd_clear_query),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun TrackDescriptor.titleLine(): String {
    val raw = title?.takeIf { it.isNotBlank() }
    return if (raw != null) bidiWrap(raw) else stringResource(Res.string.track_untitled)
}

@Composable
private fun TrackDescriptor.artistLine(): String {
    val raw = artist?.takeIf { it.isNotBlank() }
    return if (raw != null) bidiWrap(raw) else stringResource(Res.string.track_unknown_artist)
}

private data class RadioNow(
    val mode: ShuffleMode,
    val track: TrackDescriptor?,
    val queue: List<TrackDescriptor>,
    val index: Int,
)

private const val UPCOMING_LIMIT = 8
private const val MATCH_LIMIT = 12
