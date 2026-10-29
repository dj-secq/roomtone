/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.app

import io.github.nikitasud.latentjam.smart.SemanticLabel
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.TrackSemantics
import io.github.nikitasud.latentjam.smart.cluster.SonicJourney
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RadioSessionTest {

    @Test
    fun `a saved SMART resume uses the listen until playback has settled`() {
        val listen = RadioListen(artistVariety = 4)
        assertEquals(
            4,
            engineArtistVariety(
                savedLevel = 2,
                listen = listen,
                playbackIsSmart = false,
                resumeIsSmart = true,
                restoreSettled = false,
            ),
        )
        assertEquals(
            2,
            engineArtistVariety(
                savedLevel = 2,
                listen = listen,
                playbackIsSmart = false,
                resumeIsSmart = true,
                restoreSettled = true,
            ),
        )
    }

    @Test
    fun `leaving SMART returns the saved artist variety`() {
        assertEquals(
            2,
            engineArtistVariety(
                savedLevel = 2,
                listen = RadioListen(artistVariety = 0),
                playbackIsSmart = false,
                resumeIsSmart = false,
                restoreSettled = true,
            ),
        )
    }

    @Test
    fun `an active listen without an override keeps the saved level`() {
        assertEquals(
            3,
            engineArtistVariety(
                savedLevel = 3,
                listen = RadioListen(seedTrackId = "a"),
                playbackIsSmart = true,
                resumeIsSmart = false,
                restoreSettled = true,
            ),
        )
    }

    @Test
    fun `the seed is recorded once`() {
        val first = withSeedIfMissing(null, "song", smartOn = true)
        assertEquals("song", first?.seedTrackId)
        val again = withSeedIfMissing(first?.copy(artistVariety = 1), "other", smartOn = true)
        assertEquals("song", again?.seedTrackId)
        assertEquals(1, again?.artistVariety)
        assertEquals(first, withSeedIfMissing(first, "song", smartOn = false))
    }

    @Test
    fun `too repetitive steps variety once and stops at the top`() {
        assertEquals(3, bumpArtistVariety(null, savedLevel = 2)?.artistVariety)
        val atMax = RadioListen(artistVariety = 4, suppressedTrackIds = setOf("keep"))
        assertSame(atMax, bumpArtistVariety(atMax, savedLevel = 2))
        assertNull(bumpArtistVariety(null, savedLevel = 4))
    }

    @Test
    fun `more like this updates the seed and does not play`() = runTest {
        val listen = RadioListen(
            seedTrackId = "old",
            suppressedTrackIds = setOf("x"),
            artistVariety = 2,
            stationId = "r1",
        )
        val order = mutableListOf<String>()
        var stored: RadioListen? = null
        applyMoreLikeThis(
            trackId = "now",
            listen = listen,
            setListen = {
                order += "listen"
                stored = it
            },
            invalidate = { order += "invalidate" },
        )
        assertEquals(listOf("listen", "invalidate"), order)
        assertEquals("now", stored?.seedTrackId)
        assertEquals(setOf("x"), stored?.suppressedTrackIds)
        assertEquals(2, stored?.artistVariety)
        assertEquals("r1", stored?.stationId)
        applyMoreLikeThis(trackId = null, listen = listen, setListen = { order += "again" }, invalidate = { order += "no" })
        assertEquals(listOf("listen", "invalidate"), order)
    }

    @Test
    fun `not this keeps the song without touching a permanent exclusion`() {
        val once = suppressForNow(RadioListen(seedTrackId = "seed"), "skip")
        assertEquals(setOf("skip"), once?.suppressedTrackIds)
        assertEquals("seed", once?.seedTrackId)
        val again = suppressForNow(once, "skip")
        assertEquals(setOf("skip"), again?.suppressedTrackIds)
    }

    @Test
    fun `a listen round-trips and a corrupt record is dropped`() {
        val listen = RadioListen(
            seedTrackId = "seed",
            artistVariety = 1,
            suppressedTrackIds = setOf("b", "a"),
            stationId = "r1",
            stationName = "Night",
        )
        assertEquals(listen, decodeRadioListen(encodeRadioListen(listen)))
        assertEquals("", encodeRadioListen(null))
        assertEquals("", encodeRadioListen(RadioListen()))
        assertNull(decodeRadioListen("nope"))
        assertNull(decodeRadioListen(null))
    }

    @Test
    fun `stations keep the newest first and refuse a blank name`() {
        val first = RadioStation("r1", "Night", "seed", 4)
        val saved = saveRadioStation(emptyList(), first)
        val renamed = saveRadioStation(saved, first.copy(name = "  Late  "))
        assertEquals(listOf(first.copy(name = "Late")), renamed)
        assertEquals(saved, saveRadioStation(saved, first.copy(name = "  ")))
        assertEquals(saved, decodeRadioStations(encodeRadioStations(saved)))
        assertEquals(emptyList(), decodeRadioStations("bad"))
    }

    @Test
    fun `saving a name that already exists updates that station and a new name inserts`() {
        val night = RadioStation("r1", "Night", "seed", 2)
        val day = RadioStation("r2", "Day", "other", 1)
        val updated = saveNamedStation(
            listOf(night, day),
            name = " Night ",
            seedTrackId = "new",
            artistVariety = 4,
            nowMs = 10,
        )
        assertEquals(listOf("r1", "r2"), updated.map { it.id })
        assertEquals("new", updated.first().seedTrackId)
        assertEquals(4, updated.first().artistVariety)
        val inserted = saveNamedStation(updated, name = "Late", seedTrackId = "late", artistVariety = 0, nowMs = 11)
        assertEquals("r11", inserted.first().id)
        assertEquals(listOf("r11", "r1", "r2"), inserted.map { it.id })
        assertEquals(listOf(day), removeRadioStation(listOf(night, day), "r1"))
        val encoded = encodeRadioStations(inserted)
        assertTrue(encoded.lines().all { it.split("\u001f").size == 4 })
        assertFalse(encoded.contains("folder"))
        assertEquals(inserted, decodeRadioStations(encoded))
        assertEquals(RadioFilter.OFF, listenForStation(night).filter)
    }

    @Test
    fun `a six field listen stays off and a seventh field round trips the filter`() {
        val plain = RadioListen(seedTrackId = "seed", artistVariety = 1, suppressedTrackIds = setOf("a"))
        val encoded = encodeRadioListen(plain)
        assertEquals(6, encoded.split("\u001f").size)
        assertEquals(RadioFilter.OFF, decodeRadioListen(encoded)?.filter)
        val filtered = plain.copy(filter = RadioFilter.DECADE)
        val withFilter = encodeRadioListen(filtered)
        assertEquals(7, withFilter.split("\u001f").size)
        assertEquals(filtered, decodeRadioListen(withFilter))
        assertEquals(RadioFilter.OFF, decodeRadioListen("$encoded\u001fnope")?.filter)
        assertEquals("seed", decodeRadioListen("$encoded\u001fnope")?.seedTrackId)
    }

    @Test
    fun `folder and decade filters keep only matching candidates`() {
        val seed = TrackDescriptor(TrackId("s"), folderPath = "Music/Jazz", originalYear = 1994)
        val same = TrackDescriptor(TrackId("a"), folderPath = " Music/Jazz ", year = 1997)
        val other = TrackDescriptor(TrackId("b"), folderPath = "Music/Rock", originalYear = 2001)
        val songs = listOf(same, other)
        val folder = listenAllows(RadioFilter.FOLDER, seed, emptyMap())
        assertEquals(listOf(same), radioCandidates(songs, emptySet(), folder))
        val decade = listenAllows(RadioFilter.DECADE, seed, emptyMap())
        assertEquals(listOf(same), radioCandidates(songs, emptySet(), decade))
        assertEquals(1990, decadeStart(seed))
        assertEquals(1990, decadeStart(same))
        val instrumental = listenAllows(RadioFilter.INSTRUMENTAL, seed, mapOf(same.id to scores(instrumental = 0.8f, speech = 0.2f)))
        assertEquals(listOf(same), radioCandidates(songs, emptySet(), instrumental))
        assertEquals(emptyList(), radioCandidates(songs, emptySet()) { false })
    }

    @Test
    fun `instrumental stays hidden when the cache is empty`() {
        assertFalse(radioChipVisible(available = false, selected = false))
        assertTrue(radioChipVisible(available = true, selected = false))
        // A chip the listener already turned on stays, so they can turn it off.
        assertTrue(radioChipVisible(available = false, selected = true))
    }

    @Test
    fun `travel asks for a new vector space and plays nothing when it is missing`() = runTest {
        var asked: List<TrackId>? = null
        val missing = freshTravelPath(
            libraryIds = listOf(TrackId("a"), TrackId("b")),
            from = TrackId("a"),
            to = TrackId("b"),
            artistKeyOf = { null },
            loadSpace = {
                asked = it
                null
            },
        )
        assertNull(missing)
        assertEquals(listOf(TrackId("a"), TrackId("b")), asked)
        var skipped = false
        val sameSong = freshTravelPath(
            libraryIds = listOf(TrackId("a")),
            from = TrackId("a"),
            to = TrackId("a"),
            artistKeyOf = { null },
            loadSpace = {
                skipped = true
                null
            },
        )
        assertNull(sameSong)
        assertFalse(skipped)
    }

    @Test
    fun `travel plays at most ten tracks with shuffle off after the listen is cleared`() = runTest {
        val order = mutableListOf<String>()
        var played: List<TrackId>? = null
        val started = playTravel(
            path = (1..12).map { TrackId("t$it") },
            clearListen = { order += "listen" },
            clearSuppressed = { order += "clear" },
            setShuffleOff = { order += "off" },
            play = {
                order += "play"
                played = it
            },
        )
        assertTrue(started)
        assertEquals(listOf("listen", "clear", "off", "play"), order)
        assertEquals(SonicJourney.LENGTH, played?.size)
        assertEquals(TrackId("t1"), played?.first())
        assertEquals(TrackId("t10"), played?.last())
        val quiet = mutableListOf<String>()
        val missed = playTravel(
            path = null,
            clearListen = { quiet += "listen" },
            clearSuppressed = { quiet += "clear" },
            setShuffleOff = { quiet += "off" },
            play = { quiet += "play" },
        )
        assertFalse(missed)
        assertEquals(emptyList(), quiet)
    }

    @Test
    fun `an empty suppression keeps the candidate list`() {
        val songs = listOf(TrackDescriptor(TrackId("a")), TrackDescriptor(TrackId("b")))
        assertSame(songs, radioCandidates(songs, emptySet()))
        assertEquals(listOf(songs[1]), radioCandidates(songs, setOf(TrackId("a"))))
    }

    @Test
    fun `starting a station copies its seed and variety into the listen`() {
        val station = RadioStation("r9", "Drive", "song", 9)
        val listen = listenForStation(station)
        assertEquals("song", listen.seedTrackId)
        assertEquals(4, listen.artistVariety)
        assertEquals("Drive", listen.stationName)
    }

    @Test
    fun `a new radio records the seed before shuffle and play`() = runTest {
        val seed = TrackDescriptor(id = TrackId("seed"), title = "Seed")
        val order = mutableListOf<String>()
        var listen: RadioListen? = null
        beginRadio(
            seed = seed,
            setListen = {
                order += "listen"
                listen = it
            },
            clearSuppressed = { order += "clear" },
            setShuffleSmart = { order += "smart" },
            playSeed = { order += "play" },
        )
        assertEquals(listOf("listen", "clear", "smart", "play"), order)
        assertEquals(RadioListen(seedTrackId = "seed"), listen)
    }
}

private fun scores(instrumental: Float, speech: Float): TrackSemantics {
    val values = FloatArray(TrackSemantics.OUTPUT_SIZE)
    values[SemanticLabel.INSTRUMENTAL.modelIndex] = instrumental
    values[SemanticLabel.SPEECH.modelIndex] = speech
    return checkNotNull(TrackSemantics.fromModelOutput(values))
}
