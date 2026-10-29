/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.app

import io.github.nikitasud.latentjam.smart.SemanticLabel
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.TrackSemantics
import io.github.nikitasud.latentjam.smart.cluster.LibraryVectorSpace
import io.github.nikitasud.latentjam.smart.cluster.SonicJourney

/**
 * Mix choices for the SMART listen that is playing now.
 *
 * Null fields mean "use the saved setting". Nothing here is written into Don't recommend.
 * A saved [RadioStation] is a recipe for a future listen, not a playlist.
 */
data class RadioListen(
    val seedTrackId: String? = null,
    /** Index into [ARTIST_VARIETY_PENALTIES], or null to follow Settings. */
    val artistVariety: Int? = null,
    /** Tracks kept out of the rest of this listen. */
    val suppressedTrackIds: Set<String> = emptySet(),
    val stationId: String? = null,
    val stationName: String? = null,
    /** Off when the listener has not asked this listen to stay inside one fact. */
    val filter: RadioFilter = RadioFilter.OFF,
) {
    fun isRemembered(): Boolean =
        seedTrackId != null ||
            artistVariety != null ||
            suppressedTrackIds.isNotEmpty() ||
            stationId != null ||
            filter != RadioFilter.OFF
}

/**
 * The candidate gate the chooser reads. [allows] null means every non-suppressed track.
 * A key change tells the chooser its cached plan was built under a different rule.
 */
internal data class RadioCandidateGate(
    val key: String = "",
    val allows: ((TrackDescriptor) -> Boolean)? = null,
) {
    companion object {
        val Open = RadioCandidateGate()
    }
}

/**
 * One constraint for this listen. Stations do not store it.
 * [FOLDER] and [DECADE] are facts about the seed. [INSTRUMENTAL] is a cached guess.
 */
enum class RadioFilter {
    OFF,
    FOLDER,
    DECADE,
    INSTRUMENTAL,
}

/** One named SMART start: a seed and the artist-variety level to use for that listen. */
data class RadioStation(
    val id: String,
    val name: String,
    val seedTrackId: String,
    val artistVariety: Int,
)

internal const val MAX_RADIO_STATIONS: Int = 24
internal const val MAX_STATION_NAME_CHARS: Int = 40
private const val MAX_TRACK_ID_CHARS: Int = 300
private const val MAX_SUPPRESSED: Int = 32
private const val FIELD = "\u001f"
private const val LIST = "\u001e"
private const val LISTEN_VERSION = "1"
private const val FILTER_FOLDER = "folder"
private const val FILTER_DECADE = "decade"
private const val FILTER_INSTRUMENTAL = "instrumental"

/**
 * The artist-variety level the engine should hold.
 *
 * Before launch restore settles, a saved SMART session still counts: the first apply must use
 * that listen's level, or the applier would replan a queue that should resume as it was.
 * After restore, only a SMART playback mode keeps the listen's level. Any other mode returns
 * the saved setting so a session override cannot leak into the next shuffle.
 */
internal fun engineArtistVariety(
    savedLevel: Int,
    listen: RadioListen?,
    playbackIsSmart: Boolean,
    resumeIsSmart: Boolean,
    restoreSettled: Boolean,
): Int {
    val smart = if (restoreSettled) playbackIsSmart else playbackIsSmart || resumeIsSmart
    val level = if (smart) listen?.artistVariety ?: savedLevel else savedLevel
    return sanitizeArtistVariety(level)
}

/**
 * Starts a living SMART radio from [seed].
 *
 * The listen is written before shuffle mode changes, so the session records this song rather than
 * whatever was playing. Previous "not this" tracks are dropped: this is a new listen.
 */
internal suspend fun beginRadio(
    seed: TrackDescriptor,
    setListen: (RadioListen) -> Unit,
    clearSuppressed: () -> Unit,
    setShuffleSmart: suspend () -> Unit,
    playSeed: suspend () -> Unit,
) {
    setListen(RadioListen(seedTrackId = seed.id.value))
    clearSuppressed()
    setShuffleSmart()
    playSeed()
}

/** Records the song SMART started from, once, without touching any other session choice. */
internal fun withSeedIfMissing(listen: RadioListen?, trackId: String?, smartOn: Boolean): RadioListen? {
    if (!smartOn) return listen
    val seed = cleanTrackId(trackId) ?: return listen
    if (listen?.seedTrackId != null) return listen
    return (listen ?: RadioListen()).copy(seedTrackId = seed)
}

/**
 * Points this listen at [trackId] and keeps every other choice, including songs already set aside.
 * Null when [trackId] is not a usable id. The caller replans; this does not start playback.
 */
internal fun reseedListen(listen: RadioListen?, trackId: String?): RadioListen? {
    val seed = cleanTrackId(trackId) ?: return null
    return (listen ?: RadioListen()).copy(seedTrackId = seed)
}

/** Writes the reseed, then asks SMART to rebuild only the upcoming rows. */
internal suspend fun applyMoreLikeThis(
    trackId: String?,
    listen: RadioListen?,
    setListen: (RadioListen) -> Unit,
    invalidate: suspend () -> Unit,
) {
    val updated = reseedListen(listen, trackId) ?: return
    setListen(updated)
    invalidate()
}

/** One step more variety for this listen. Already at the top step, the listen is unchanged. */
internal fun bumpArtistVariety(listen: RadioListen?, savedLevel: Int): RadioListen? {
    val current = sanitizeArtistVariety(listen?.artistVariety ?: savedLevel)
    val next = sanitizeArtistVariety(current + 1)
    if (next == current) return listen
    return (listen ?: RadioListen()).copy(artistVariety = next)
}

/** Keeps [trackId] out of the rest of this listen. */
internal fun suppressForNow(listen: RadioListen?, trackId: String): RadioListen? {
    val id = cleanTrackId(trackId) ?: return listen
    val suppressed = (listen?.suppressedTrackIds.orEmpty() + id).toList().takeLast(MAX_SUPPRESSED).toSet()
    return (listen ?: RadioListen()).copy(suppressedTrackIds = suppressed)
}

internal fun listenForStation(station: RadioStation): RadioListen = RadioListen(
    seedTrackId = station.seedTrackId,
    artistVariety = sanitizeArtistVariety(station.artistVariety),
    stationId = station.id,
    stationName = station.name,
)

internal fun suppressedAsTrackIds(listen: RadioListen?): Set<TrackId> =
    listen?.suppressedTrackIds?.mapTo(HashSet()) { TrackId(it) }.orEmpty()

internal fun newRadioStationId(nowMs: Long, existing: List<RadioStation>): String {
    var n = nowMs.coerceAtLeast(0L)
    val ids = existing.mapTo(HashSet()) { it.id }
    while ("r$n" in ids) n++
    return "r$n"
}

/**
 * Puts [station] first and drops anything past [MAX_RADIO_STATIONS].
 * A blank name or a missing seed leaves the list alone.
 */
internal fun saveRadioStation(existing: List<RadioStation>, station: RadioStation): List<RadioStation> {
    val clean = station.sanitized() ?: return existing
    return (listOf(clean) + existing.filter { it.id != clean.id }).take(MAX_RADIO_STATIONS)
}

/**
 * Saves [name] as a station for [seedTrackId].
 * The same name after trim updates that station's seed and variety and moves it to the front.
 * A new name is inserted, newest first. A blank name leaves the list alone.
 */
internal fun saveNamedStation(
    existing: List<RadioStation>,
    name: String,
    seedTrackId: String,
    artistVariety: Int,
    nowMs: Long,
): List<RadioStation> {
    val nameKey = stationNameKey(name)
    if (nameKey.isEmpty() || cleanTrackId(seedTrackId) == null) return existing
    val match = existing.firstOrNull { it.name == nameKey }
    val station = if (match != null) {
        match.copy(name = nameKey, seedTrackId = seedTrackId, artistVariety = artistVariety)
    } else {
        RadioStation(
            id = newRadioStationId(nowMs, existing),
            name = nameKey,
            seedTrackId = seedTrackId,
            artistVariety = artistVariety,
        )
    }
    return saveRadioStation(existing, station)
}

/** Drops the station with [id]. The song itself stays in the library. */
internal fun removeRadioStation(existing: List<RadioStation>, id: String): List<RadioStation> =
    existing.filterNot { it.id == id }

internal fun stationNameKey(name: String): String =
    name.trim().replace('\n', ' ').replace(FIELD, " ").take(MAX_STATION_NAME_CHARS).trim()

internal fun RadioStation.sanitized(): RadioStation? {
    val id = id.trim().takeIf { it.length in 1..40 && it.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } }
        ?: return null
    val name = stationNameKey(name)
    val seed = cleanTrackId(seedTrackId) ?: return null
    if (name.isEmpty()) return null
    return copy(id = id, name = name, seedTrackId = seed, artistVariety = sanitizeArtistVariety(artistVariety))
}

internal fun encodeRadioListen(listen: RadioListen?): String {
    val stored = listen?.takeIf { it.isRemembered() } ?: return ""
    val fields = mutableListOf(
        LISTEN_VERSION,
        stored.seedTrackId.orEmpty(),
        stored.artistVariety?.let(::sanitizeArtistVariety)?.toString().orEmpty(),
        stored.suppressedTrackIds.mapNotNull(::cleanTrackId).sorted().joinToString(LIST),
        stored.stationId.orEmpty(),
        stored.stationName.orEmpty(),
    )
    // A missing seventh field means off, so an unfiltered listen stays a six-field record.
    if (stored.filter != RadioFilter.OFF) fields += filterToken(stored.filter)
    return fields.joinToString(FIELD)
}

internal fun decodeRadioListen(raw: String?): RadioListen? {
    if (raw.isNullOrBlank()) return null
    val fields = raw.split(FIELD)
    if ((fields.size != 6 && fields.size != 7) || fields[0] != LISTEN_VERSION) return null
    val listen = RadioListen(
        seedTrackId = cleanTrackId(fields[1]),
        artistVariety = fields[2].toIntOrNull()?.let(::sanitizeArtistVariety),
        suppressedTrackIds = fields[3].split(LIST).mapNotNull(::cleanTrackId).toSet(),
        stationId = fields[4].trim().takeIf { it.isNotEmpty() },
        stationName = fields[5].trim().takeIf { it.isNotEmpty() },
        filter = if (fields.size == 7) filterFromToken(fields[6]) else RadioFilter.OFF,
    )
    return listen.takeIf { it.isRemembered() }
}

internal fun encodeRadioStations(stations: List<RadioStation>): String =
    stations.mapNotNull { it.sanitized() }.take(MAX_RADIO_STATIONS).joinToString("\n") { station ->
        listOf(station.id, station.name, station.seedTrackId, station.artistVariety.toString()).joinToString(FIELD)
    }

internal fun decodeRadioStations(raw: String?): List<RadioStation> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        val fields = line.split(FIELD)
        if (fields.size != 4) return@mapNotNull null
        RadioStation(
            id = fields[0],
            name = fields[1],
            seedTrackId = fields[2],
            artistVariety = fields[3].toIntOrNull() ?: return@mapNotNull null,
        ).sanitized()
    }.take(MAX_RADIO_STATIONS).toList()
}

private fun filterToken(filter: RadioFilter): String = when (filter) {
    RadioFilter.FOLDER -> FILTER_FOLDER
    RadioFilter.DECADE -> FILTER_DECADE
    RadioFilter.INSTRUMENTAL -> FILTER_INSTRUMENTAL
    RadioFilter.OFF -> ""
}

/** An unknown token stays off so a newer record does not drop the rest of the listen. */
private fun filterFromToken(token: String): RadioFilter = when (token) {
    FILTER_FOLDER -> RadioFilter.FOLDER
    FILTER_DECADE -> RadioFilter.DECADE
    FILTER_INSTRUMENTAL -> RadioFilter.INSTRUMENTAL
    else -> RadioFilter.OFF
}

/** The seed's folder, or null when this listen has no folder fact to offer. */
internal fun folderKey(track: TrackDescriptor?): String? =
    track?.folderPath?.trim()?.takeIf { it.isNotEmpty() }

/** [originalYear], or [TrackDescriptor.year] when the original is missing, rounded down to a decade. */
internal fun decadeStart(track: TrackDescriptor?): Int? {
    val year = track?.originalYear ?: track?.year ?: return null
    return year.floorDiv(10) * 10
}

/** Instrumental beats speech only when the score is strictly higher. */
internal fun instrumentalBeatsSpeech(semantics: TrackSemantics): Boolean =
    semantics.probability(SemanticLabel.INSTRUMENTAL) > semantics.probability(SemanticLabel.SPEECH)

/**
 * Whether a filter chip can be shown.
 * A selected chip stays visible when its fact disappears, so the listener can turn it off.
 * An unselected chip stays hidden: instrumental, in particular, when the semantic cache is empty.
 */
internal fun radioChipVisible(available: Boolean, selected: Boolean): Boolean = available || selected

/** Null [allows] means the filter is off and the candidate list is not copied. */
internal fun listenAllows(
    filter: RadioFilter,
    seed: TrackDescriptor?,
    semantics: Map<TrackId, TrackSemantics>,
): ((TrackDescriptor) -> Boolean)? = when (filter) {
    RadioFilter.OFF -> null
    RadioFilter.FOLDER -> {
        val folder = folderKey(seed)
        if (folder == null) ({ false }) else ({ folderKey(it) == folder })
    }
    RadioFilter.DECADE -> {
        val decade = decadeStart(seed)
        if (decade == null) ({ false }) else ({ decadeStart(it) == decade })
    }
    RadioFilter.INSTRUMENTAL -> { track ->
        semantics[track.id]?.let(::instrumentalBeatsSpeech) == true
    }
}

internal fun listenFilterKey(
    filter: RadioFilter,
    seed: TrackDescriptor?,
    semanticsReady: Boolean,
): String = when (filter) {
    RadioFilter.OFF -> ""
    RadioFilter.FOLDER -> "folder:${folderKey(seed).orEmpty()}"
    RadioFilter.DECADE -> "decade:${decadeStart(seed)?.toString().orEmpty()}"
    RadioFilter.INSTRUMENTAL -> "instrumental:${if (semanticsReady) 1 else 0}"
}

internal fun radioGateFor(
    listen: RadioListen?,
    library: List<TrackDescriptor>,
    semantics: Map<TrackId, TrackSemantics>,
): RadioCandidateGate {
    val filter = listen?.filter ?: RadioFilter.OFF
    val seed = listen?.seedTrackId?.let { id -> library.firstOrNull { it.id.value == id } }
    return RadioCandidateGate(
        key = listenFilterKey(filter, seed, semantics.isNotEmpty()),
        allows = listenAllows(filter, seed, semantics),
    )
}

internal fun travelArtistKey(track: TrackDescriptor?): String? =
    track?.artist?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

/**
 * A path from [from] to [to] on a vector space loaded for this call.
 *
 * [loadSpace] must build a new space. The space a world already clustered cannot be reused.
 * Null when either song has no vector, the songs are the same, or the path cannot be filled.
 * The result has at most [SonicJourney.LENGTH] ids.
 */
internal suspend fun freshTravelPath(
    libraryIds: List<TrackId>,
    from: TrackId,
    to: TrackId,
    artistKeyOf: (TrackId) -> String?,
    loadSpace: suspend (List<TrackId>) -> LibraryVectorSpace?,
): List<TrackId>? {
    if (from == to) return null
    val space = loadSpace(libraryIds) ?: return null
    return SonicJourney.session(space).plot(from, to, artistKeyOf)?.take(SonicJourney.LENGTH)
}

/**
 * Plays [path] as an ordinary queue.
 *
 * The listen and its suppressions are cleared before shuffle turns off, and shuffle is off
 * before play, so a SMART session cannot collapse the path to one track. A missing or short
 * path plays nothing and leaves the listen where it was.
 */
internal suspend fun playTravel(
    path: List<TrackId>?,
    clearListen: () -> Unit,
    clearSuppressed: () -> Unit,
    setShuffleOff: suspend () -> Unit,
    play: suspend (List<TrackId>) -> Unit,
): Boolean {
    val queue = path?.take(SonicJourney.LENGTH)?.takeIf { it.size >= 2 } ?: return false
    clearListen()
    clearSuppressed()
    setShuffleOff()
    play(queue)
    return true
}

private fun cleanTrackId(raw: String?): String? =
    raw?.trim()?.takeIf { id ->
        id.isNotEmpty() &&
            id.length <= MAX_TRACK_ID_CHARS &&
            FIELD !in id &&
            LIST !in id &&
            '\n' !in id
    }
