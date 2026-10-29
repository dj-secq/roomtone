/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.app

import io.github.nikitasud.latentjam.history.ListenEvent
import io.github.nikitasud.latentjam.history.TrackStats
import io.github.nikitasud.latentjam.library.Playlist
import io.github.nikitasud.latentjam.smart.SemanticLabel
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.TrackSemantics
import io.github.nikitasud.latentjam.smart.cluster.LibraryWorld
import io.github.nikitasud.latentjam.smart.cluster.LibraryWorldContent
import io.github.nikitasud.latentjam.smart.cluster.LibraryWorldNameSource
import io.github.nikitasud.latentjam.smart.cluster.LibraryWorldSemanticTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The selection rules, pinned. These are the parts that decide what a listener is shown, and they
 * are easy to break silently — a wrong comparison produces a page that still looks plausible.
 */
class ForYouBuilderTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000

    /**
     * The page's day boundary, pinned to the UTC midnight the fixtures below are built around.
     * Production keys every rotation to the device's LOCAL day (see `localDayOf` on
     * [ForYouBuilder.build]), and a fixture that left it to the default would silently move to
     * another day with the runner's timezone.
     */
    private val utcDay: (Long) -> Long = { at -> at / day }

    private fun track(id: String, artist: String = "A", added: Long = 0) =
        TrackDescriptor(id = TrackId(id), title = "T$id", artist = artist, addedAtMs = added)

    private fun stats(plays: Int, completions: Int = plays, skips: Int = 0, last: Long) =
        TrackStats(
            plays = plays,
            completions = completions,
            skips = skips,
            totalPlayedMs = 0,
            lastPlayedAtMs = last,
        )

    private fun sections(
        library: List<TrackDescriptor>,
        stats: Map<TrackId, TrackStats> = emptyMap(),
        events: List<ListenEvent> = emptyList(),
    ) = ForYouBuilder.build(library, stats, events, now, localDayOf = utcDay).sections

    private fun page(
        library: List<TrackDescriptor>,
        stats: Map<TrackId, TrackStats> = emptyMap(),
        events: List<ListenEvent> = emptyList(),
        playlists: List<Playlist> = emptyList(),
    ) = ForYouBuilder.build(
        library,
        stats,
        events,
        now,
        playlists = playlists,
        localDayOf = utcDay,
    )

    private fun track(id: String, artist: String, album: String) =
        TrackDescriptor(id = TrackId(id), title = "T$id", artist = artist, album = album)

    @Test
    fun `a loved track gone quiet is worth revisiting`() {
        // Four, because the hero claims the strongest candidate and the shelf needs three more to
        // establish a pattern rather than making a row from one isolated record.
        val tracks = (1..4).map { track("$it", artist = "Artist$it") }
        val result = sections(
            library = tracks,
            stats = tracks.mapIndexed { index, descriptor ->
                descriptor.id to stats(plays = 10 - index, last = now - 120 * day)
            }.toMap(),
        )
        val section = result.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        // The hero rotates through the strongest few day by day, so pin the shape rather than
        // one fixed winner: the shelf holds exactly the loved tracks the hero did not claim, in
        // completion order, each captioned with its own play count.
        val heroClaims = tracks.map { it.id }.toSet() - section.cards.map { it.track.id }.toSet()
        assertEquals(1, heroClaims.size)
        assertEquals(
            tracks.map { it.id }.filterNot { it in heroClaims },
            section.cards.map { it.track.id },
        )
        val firstShown = section.cards.first().track.id
        val expectedPlays = 10 - tracks.indexOfFirst { it.id == firstShown }
        assertEquals(ForYouCaption.PlayedBefore(expectedPlays), section.cards.first().caption)
    }

    @Test
    fun `worth revisiting waits for enough dormant evidence`() {
        val tracks = (1..3).map { track("$it", artist = "Artist$it") }
        val result = sections(
            library = tracks,
            stats = tracks.associate { it.id to stats(plays = 8, last = now - 120 * day) },
        )

        // The hero may confidently resurface one track, but two remaining records are not enough
        // to manufacture a whole shelf.
        assertTrue(result.none { it.kind == ForYouSectionKind.WORTH_REVISITING })
    }

    @Test
    fun `a track played last month is not forgotten`() {
        val recent = track("1")
        val result = sections(
            library = listOf(recent),
            stats = mapOf(recent.id to stats(plays = 10, last = now - 30 * day)),
        )
        // 30 days is an ordinary gap; surfacing it would claim an absence the listener never felt.
        assertTrue(result.none { it.kind == ForYouSectionKind.WORTH_REVISITING })
    }

    @Test
    fun `a track skipped more than finished is not resurfaced`() {
        val disliked = track("1")
        val result = sections(
            library = listOf(disliked),
            stats = mapOf(
                disliked.id to stats(plays = 10, completions = 2, skips = 8, last = now - 200 * day),
            ),
        )
        assertTrue(result.none { it.kind == ForYouSectionKind.WORTH_REVISITING })
    }

    @Test
    fun `one artist cannot own a row`() {
        val library = (1..6).map { track("$it", artist = "Same") }
        val result = sections(
            library = library,
            stats = library.associate { it.id to stats(plays = 5, last = now - 200 * day) },
        )
        val section = result.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        assertEquals(ForYouBuilder.MAX_PER_ARTIST, section.cards.size)
    }

    @Test
    fun `a track shown in one section is not repeated in another`() {
        val shared = track("1")
        val result = page(
            library = listOf(shared),
            stats = mapOf(shared.id to stats(plays = 5, last = now - 200 * day)),
            events = listOf(
                ListenEvent(
                    trackId = shared.id,
                    startedAtMs = now - 400 * day,
                    playedMs = 1000,
                    trackDurationMs = 1000,
                    completed = true,
                    skipped = false,
                    shuffleMode = "SMART",
                ),
            ),
        )
        val appearances = result.sections.sumOf { section ->
            section.cards.count { it.track.id == shared.id }
        } + if (result.hero?.track?.id == shared.id) 1 else 0
        assertEquals(1, appearances)
    }

    @Test
    fun `an empty history offers a radio start and no history row`() {
        val library = listOf(track("1"), track("2", artist = "B"))
        val result = page(library = library)
        assertEquals(listOf(ForYouSectionKind.RADIO), result.sections.map { it.kind })
        assertEquals(ForYouCaption.NeverPlayed, result.sections.single().cards.single().caption)
        assertEquals(ForYouKicker.NeverPlayed, result.hero?.kicker)
    }

    @Test
    fun `an interrupted track becomes the hero with its resume point`() {
        val abandoned = track("1")
        val result = page(
            library = listOf(abandoned),
            events = listOf(
                ListenEvent(
                    trackId = abandoned.id,
                    startedAtMs = now - day,
                    playedMs = 90_000,
                    trackDurationMs = 600_000,
                    completed = false,
                    skipped = false,
                ),
            ),
        )
        assertEquals(abandoned.id, result.hero?.track?.id)
        assertEquals(90_000L, result.hero?.resumeAtMs)
    }

    @Test
    fun `a recording finished after it was abandoned is no longer resumable`() {
        val set = track("1")
        val result = page(
            library = listOf(set),
            // Newest first, as recentEvents returns them: abandoned two days ago, then played through.
            events = listOf(
                ListenEvent(
                    trackId = set.id,
                    startedAtMs = now - day,
                    playedMs = 600_000,
                    trackDurationMs = 600_000,
                    completed = true,
                    skipped = false,
                ),
                ListenEvent(
                    trackId = set.id,
                    startedAtMs = now - 2 * day,
                    playedMs = 90_000,
                    trackDurationMs = 600_000,
                    completed = false,
                    skipped = false,
                ),
            ),
        )
        assertEquals(null, result.hero?.resumeAtMs)
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.CONTINUE })
    }

    @Test
    fun `an interruption older than the resume window is not offered`() {
        val set = track("1")
        val result = page(
            library = listOf(set),
            events = listOf(
                ListenEvent(
                    trackId = set.id,
                    startedAtMs = now - 15 * day,
                    playedMs = 90_000,
                    trackDurationMs = 600_000,
                    completed = false,
                    skipped = false,
                ),
            ),
        )
        assertEquals(null, result.hero?.resumeAtMs)
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.CONTINUE })
    }

    @Test
    fun `a track barely started is not offered as resumable`() {
        val auditioned = track("1")
        val result = page(
            library = listOf(auditioned),
            events = listOf(
                ListenEvent(
                    trackId = auditioned.id,
                    startedAtMs = now - day,
                    // Under the resume floor: this was an audition, not an interruption.
                    playedMs = 20_000,
                    trackDurationMs = 240_000,
                    completed = false,
                    skipped = true,
                ),
            ),
        )
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.CONTINUE })
        assertEquals(null, result.hero?.resumeAtMs)
    }

    @Test
    fun `an ordinary song restarts instead of becoming continue listening`() {
        val song = track("1")
        val result = page(
            library = listOf(song),
            events = listOf(
                ListenEvent(
                    trackId = song.id,
                    startedAtMs = now - day,
                    playedMs = 90_000,
                    trackDurationMs = 240_000,
                    completed = false,
                    skipped = false,
                ),
            ),
        )
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.CONTINUE })
        assertEquals(null, result.hero?.resumeAtMs)
    }

    @Test
    fun `the hero falls back to a forgotten favourite then to something unheard`() {
        val loved = track("1")
        val withHistory = page(
            library = listOf(loved),
            stats = mapOf(loved.id to stats(plays = 7, last = now - 200 * day)),
        )
        assertEquals(ForYouKicker.PlayedTimes(7), withHistory.hero?.kicker)

        val fresh = page(library = listOf(track("2", artist = "B")))
        assertEquals(ForYouKicker.NeverPlayed, fresh.hero?.kicker)
    }

    @Test
    fun `the hero is not repeated in the rows below it`() {
        val loved = track("1")
        val other = track("2", artist = "B")
        val result = page(
            library = listOf(loved, other),
            stats = mapOf(
                loved.id to stats(plays = 9, last = now - 200 * day),
                other.id to stats(plays = 4, last = now - 200 * day),
            ),
        )
        val heroId = result.hero?.track?.id
        assertTrue(result.sections.none { s -> s.cards.any { it.track.id == heroId } })
    }

    @Test
    fun `never played is newest first`() {
        val old = track("1", artist = "A", added = 100)
        val middle = track("2", artist = "B", added = 500)
        val new = track("3", artist = "C", added = 900)
        val result = page(library = listOf(old, new, middle))
        val row = result.sections
            .first { it.kind == ForYouSectionKind.NEVER_PLAYED }
            .cards.map { it.track.id }
        // Whatever the hero claimed, what remains is still ordered newest first.
        assertEquals(row.sortedByDescending { id -> listOf(old, middle, new).first { it.id == id }.addedAtMs }, row)
    }

    @Test
    fun `first open hero is the newest unheard track`() {
        val old = track("1", artist = "A", added = 100)
        val newest = track("2", artist = "B", added = 900)
        val middle = track("3", artist = "C", added = 500)

        val result = page(library = listOf(old, middle, newest))

        assertEquals(newest.id, result.hero?.track?.id)
        assertEquals(ForYouKicker.NeverPlayed, result.hero?.kicker)
    }

    @Test
    fun `a dormant playlist is offered as one card and not as its tracks`() {
        val members = (1..4).map { track("$it", artist = "Artist$it") }
        val result = page(
            library = members,
            stats = members.associate { it.id to stats(plays = 6, last = now - 200 * day) },
            playlists = listOf(Playlist(id = "p1", name = "Think", trackIds = members.map { it.id.value })),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        val collections = row.cards.mapNotNull { it.collection }
        assertEquals(listOf("Think"), collections.map { it.title })
        assertEquals("p1", collections.single().playlistId)
        // The caption counts what actually went quiet, not the playlist's length: the hero claimed
        // one of the four before the row was built. It carries the number, not a rendered string,
        // so the UI can pick the right plural form for it.
        assertEquals(
            ForYouCaption.TrackCount(3),
            row.cards.first { it.collection != null }.caption,
        )
        // The tracks it absorbed must not also stand alone beneath it.
        val loose = row.cards.filter { it.collection == null }.map { it.track.id }
        assertTrue(loose.none { id -> members.any { it.id == id } }, "absorbed tracks reappeared: $loose")
    }

    @Test
    fun `two dormant tracks are coincidence and not a playlist worth offering`() {
        val members = (1..2).map { track("$it", artist = "Artist$it") }
        val result = page(
            library = members,
            stats = members.associate { it.id to stats(plays = 6, last = now - 200 * day) },
            playlists = listOf(Playlist(id = "p1", name = "Think", trackIds = members.map { it.id.value })),
        )
        val row = result.sections.firstOrNull { it.kind == ForYouSectionKind.WORTH_REVISITING }
        assertTrue(row?.cards?.all { it.collection == null } ?: true, "two tracks should not collapse")
    }

    @Test
    fun `an album collapses when enough of it went quiet`() {
        // Four: the hero claims one, and three must remain for the album to be worth offering.
        val members = (1..4).map { track("$it", artist = "A", album = "Gold") }
        val result = page(
            library = members,
            stats = members.associate { it.id to stats(plays = 6, last = now - 200 * day) },
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        assertEquals(listOf("Gold"), row.cards.mapNotNull { it.collection }.map { it.title })
    }

    @Test
    fun `a world is one card standing for the whole region`() {
        val members = (1..12).map { track("$it", artist = "Artist$it") }
        val result = ForYouBuilder.build(
            library = members,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Hard Rock", members)),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORLDS }
        assertEquals(1, row.cards.size)
        assertEquals("Hard Rock", row.cards.single().collection?.title)
        assertEquals(ForYouCaption.TrackCount(12), row.cards.single().caption)
    }

    @Test
    fun `a large region becomes a focused mix rather than a hundred track dump`() {
        val members = (1..120).map { track("$it", artist = "Artist$it") }
        val result = ForYouBuilder.build(
            library = members,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Focused", members)),
        )

        val card = result.sections.first { it.kind == ForYouSectionKind.WORLDS }.cards.single()
        assertEquals(ForYouBuilder.MIX_TRACK_LIMIT, card.collection?.tracks?.size)
        assertEquals(ForYouCaption.TrackCount(ForYouBuilder.MIX_TRACK_LIMIT), card.caption)
    }

    @Test
    fun `a genre mix balances its primary artists and keeps the cover first`() {
        val members = listOf(
            track("e1", artist = "Eminem"),
            track("e2", artist = "Eminem feat. Nate Dogg"),
            track("e3", artist = "Eminem featuring D-12"),
            track("e4", artist = "Eminem"),
            track("p1", artist = "2Pac"),
            track("b1", artist = "The Notorious B.I.G."),
            track("n1", artist = "Nas"),
            track("j1", artist = "Jay-Z"),
        )
        val result = ForYouBuilder.build(
            library = members,
            stats = members.associate { it.id to stats(plays = 1, last = now - day) },
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Rap", members)),
        )

        val tracks = result.sections
            .first { it.kind == ForYouSectionKind.WORLDS }
            .cards.single()
            .collection
            ?.tracks
            .orEmpty()
        assertEquals(members.first().id, tracks.first().id)
        assertEquals(3, tracks.count { it.artist?.startsWith("Eminem") == true })
        assertTrue(tracks.zipWithNext().none { (left, right) ->
            left.artist?.startsWith("Eminem") == true &&
                right.artist?.startsWith("Eminem") == true
        })
    }

    @Test
    fun `generic mix labels are localized and numbered without borrowing a track title`() {
        val first = (1..6).map { track("a$it", artist = "Artist A$it") }
        val second = (1..6).map { track("b$it", artist = "Artist B$it") }
        val result = ForYouBuilder.build(
            library = first + second,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(
                LibraryWorld("ignored title", first, LibraryWorldNameSource.GENERIC),
                LibraryWorld("another ignored title", second, LibraryWorldNameSource.GENERIC),
            ),
            discoveryMixLabel = "Микс открытий",
        )

        val names = result.sections.first { it.kind == ForYouSectionKind.WORLDS }
            .cards.mapNotNull { it.collection?.title }
        assertEquals(listOf("Микс открытий 1", "Микс открытий 2"), names)
    }

    @Test
    fun `novelty and effects are hidden by default and explicit opt in reveals localized titles`() {
        val novelty = (1..5).map { track("n$it", artist = "Creator N$it") }
        val effects = (1..5).map { track("e$it", artist = "Creator E$it") }
        val worlds = listOf(
            LibraryWorld(
                name = "Meme & Viral Audio",
                tracks = novelty,
                nameSource = LibraryWorldNameSource.SEMANTIC,
                content = LibraryWorldContent.NOVELTY,
                semanticTitle = LibraryWorldSemanticTitle.MEME_VIRAL_AUDIO,
            ),
            LibraryWorld(
                name = "Sound Effects",
                tracks = effects,
                nameSource = LibraryWorldNameSource.SEMANTIC,
                content = LibraryWorldContent.SOUND_EFFECTS,
                semanticTitle = LibraryWorldSemanticTitle.SOUND_EFFECTS,
            ),
        )

        val hidden = ForYouBuilder.build(
            library = novelty + effects,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = worlds,
        )
        assertTrue(hidden.sections.none { it.kind == ForYouSectionKind.WORLDS })

        val visible = ForYouBuilder.build(
            library = novelty + effects,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = worlds,
            includeNoveltyMixes = true,
            semanticMixLabels = mapOf(
                LibraryWorldSemanticTitle.MEME_VIRAL_AUDIO to "Мемы и вирусное аудио",
                LibraryWorldSemanticTitle.SOUND_EFFECTS to "Звуковые эффекты",
            ),
        )
        val titles = visible.sections
            .single { it.kind == ForYouSectionKind.WORLDS }
            .cards
            .mapNotNull { it.collection?.title }
            .toSet()

        assertEquals(setOf("Мемы и вирусное аудио", "Звуковые эффекты"), titles)
    }

    @Test
    fun `a trimmed world stays short instead of borrowing unrelated library tracks`() {
        val confidentMembers = (1..11).map { track("core$it", artist = "Core Artist $it") }
        val unrelated = (1..40).map { track("other$it", artist = "Other Artist $it") }
        val result = ForYouBuilder.build(
            library = confidentMembers + unrelated,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Coherent core", confidentMembers)),
        )

        val card = result.sections
            .single { it.kind == ForYouSectionKind.WORLDS }
            .cards
            .single()
        val mixTracks = card.collection?.tracks.orEmpty()

        assertEquals(11, mixTracks.size)
        assertEquals(ForYouCaption.TrackCount(11), card.caption)
        assertEquals(confidentMembers.map { it.id }.toSet(), mixTracks.map { it.id }.toSet())
    }

    @Test
    fun `the world the listener actually lives in comes first`() {
        val quiet = (1..5).map { track("q$it", artist = "Quiet$it") }
        val loved = (1..5).map { track("l$it", artist = "Loved$it") }
        val result = ForYouBuilder.build(
            library = quiet + loved,
            // The regions are found without reference to listening; which one leads is not.
            stats = loved.associate { it.id to stats(plays = 20, last = now - day) },
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Quiet", quiet), LibraryWorld("Loved", loved)),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORLDS }
        assertEquals(listOf("Loved", "Quiet"), row.cards.mapNotNull { it.collection?.title })
    }

    @Test
    fun `a completed member leads an equally fresh skipped member`() {
        val hero = track("hero", artist = "Hero")
        val skipped = track("skipped", artist = "Skipped")
        val completed = track("completed", artist = "Completed")
        val result = ForYouBuilder.build(
            library = listOf(hero, skipped, completed),
            stats = mapOf(
                hero.id to stats(plays = 20, completions = 20, last = now - 200 * day),
                skipped.id to stats(plays = 8, completions = 0, skips = 8, last = now - day),
                completed.id to stats(plays = 8, completions = 8, skips = 0, last = now - day),
            ),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Feedback", listOf(skipped, completed))),
        )

        val card = result.sections.first { it.kind == ForYouSectionKind.WORLDS }.cards.single()
        assertEquals(completed.id, card.track.id)
        assertEquals(completed.id, card.collection?.tracks?.first()?.id)
    }

    @Test
    fun `a world does not retire its members from the rows below it`() {
        val members = (1..6).map { track("$it", artist = "Artist$it") }
        val result = ForYouBuilder.build(
            library = members,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Disco", members)),
        )
        // A world's pool is the whole library, so consuming it would starve every later row for the
        // sake of a rule about the same album showing up three times.
        val neverPlayed = result.sections.first { it.kind == ForYouSectionKind.NEVER_PLAYED }
        assertTrue(neverPlayed.cards.isNotEmpty(), "the worlds row swallowed the library")
    }

    @Test
    fun `a world whose centre is already on the page shows the next track in and starts there`() {
        val members = (1..6).map { track("$it", artist = "Artist$it") }
        val result = ForYouBuilder.build(
            library = members,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Disco", members)),
        )
        val heroId = result.hero?.track?.id
        assertTrue(members.any { it.id == heroId }, "the fixture needs a member as the hero")

        val card = result.sections.first { it.kind == ForYouSectionKind.WORLDS }.cards.single()
        // Not the hero's track, because that cover is already on the page — but still the most
        // central track left, taken from the ordering rather than chosen some other way.
        assertEquals(members.first { it.id != heroId }.id, card.track.id)
        // And the list starts on the same record the card shows. A card that pictures one track and
        // plays another is the smallest possible way to look broken.
        assertEquals(card.track.id, card.collection?.tracks?.first()?.id)
        assertEquals(members.size, card.collection?.tracks?.size)
    }

    @Test
    fun `a mix prefers an unheard central track over one played this week`() {
        val hero = track("hero", artist = "Hero")
        val recent = track("recent", artist = "Recent")
        val unheard = track("unheard", artist = "Unheard")
        val older = track("older", artist = "Older")
        val spare = track("aaa", artist = "Spare")
        val world = listOf(recent, unheard, older)
        val result = ForYouBuilder.build(
            library = listOf(hero, spare) + world,
            stats = mapOf(
                hero.id to stats(plays = 20, last = now - 200 * day),
                recent.id to stats(plays = 8, last = now - day),
            ),
            recentEvents = emptyList(),
            nowMs = now,
            excluded = setOf(older.id),
            worlds = listOf(LibraryWorld("Focused mix", world)),
        )

        val card = result.sections.first { it.kind == ForYouSectionKind.WORLDS }.cards.single()
        assertEquals(unheard.id, card.track.id)
        assertEquals(unheard.id, card.collection?.tracks?.first()?.id)
        assertTrue(
            card.collection?.tracks?.none { it.id == older.id } == true,
            "an excluded track remained playable inside the generated mix",
        )
    }

    @Test
    fun `no worlds means no row`() {
        val result = page(library = listOf(track("1"), track("2", artist = "B")))
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.WORLDS })
    }

    @Test
    fun `a playlist claims its tracks before the album does`() {
        // Same tracks belong to both; the playlist is deliberate, the shared album is incidental.
        val members = (1..4).map { track("$it", artist = "A", album = "Gold") }
        val result = page(
            library = members,
            stats = members.associate { it.id to stats(plays = 6, last = now - 200 * day) },
            playlists = listOf(Playlist(id = "p1", name = "Think", trackIds = members.map { it.id.value })),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        assertEquals(listOf("Think"), row.cards.mapNotNull { it.collection }.map { it.title })
    }

    @Test
    fun `a young log shrinks the quiet window so the shelf exists from week one`() {
        // The whole log is 20 days old; under a fixed 90-day window this listener would not see
        // the page's flagship section until month four of owning the app.
        val tracks = (1..4).map { track("$it", artist = "Artist$it") }
        val result = sections(
            library = tracks,
            stats = tracks.associate { it.id to stats(plays = 6, last = now - 12 * day) },
        )
        assertTrue(result.any { it.kind == ForYouSectionKind.WORTH_REVISITING })
    }

    @Test
    fun `the page rotates across days but holds still within one`() {
        val tracks = (1..30).map { track("$it", artist = "Artist$it", added = it.toLong()) }
        val sameDay = ForYouBuilder.build(
            tracks, emptyMap(), emptyList(), now + hour, localDayOf = utcDay,
        )
        val today = ForYouBuilder.build(tracks, emptyMap(), emptyList(), now, localDayOf = utcDay)
        assertEquals(today, sameDay, "two openings on one day must agree")

        val tomorrow = ForYouBuilder.build(
            tracks, emptyMap(), emptyList(), now + day, localDayOf = utcDay,
        )
        assertTrue(today != tomorrow, "consecutive days must not show an identical page")
    }

    @Test
    fun `the daily rotation turns over on the local day rather than the UTC day`() {
        val tracks = (1..30).map { track("$it", artist = "Artist$it", added = it.toLong()) }
        // A fixed UTC+13 zone — far enough east for its calendar day to disagree with UTC's for
        // eleven hours out of twenty-four. Local midnight there falls at 11:00 UTC.
        val offset = 13 * hour
        fun localDay(at: Long): Long = (at + offset) / day
        fun pageAt(at: Long) = ForYouBuilder.build(
            library = tracks,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = at,
            localDayOf = ::localDay,
        )

        // Two instants inside ONE UTC day, on either side of the listener's own midnight.
        val dayStart = now - now % day
        val beforeLocalMidnight = dayStart + 10 * hour + 30 * 60_000L
        val afterLocalMidnight = dayStart + 11 * hour + 30 * 60_000L
        assertEquals(beforeLocalMidnight / day, afterLocalMidnight / day)
        assertEquals(localDay(beforeLocalMidnight) + 1, localDay(afterLocalMidnight))
        assertTrue(
            pageAt(beforeLocalMidnight) != pageAt(afterLocalMidnight),
            "the listener's own day turned over; the page must not keep yesterday's rotation",
        )

        // And two instants inside ONE local day while UTC midnight passes between them.
        val beforeUtcMidnight = dayStart + 23 * hour + 30 * 60_000L
        val afterUtcMidnight = dayStart + 24 * hour + 30 * 60_000L
        assertEquals(beforeUtcMidnight / day + 1, afterUtcMidnight / day)
        assertEquals(localDay(beforeUtcMidnight), localDay(afterUtcMidnight))
        assertEquals(
            pageAt(beforeUtcMidnight),
            pageAt(afterUtcMidnight),
            "UTC midnight is not this listener's midnight",
        )
    }

    @Test
    fun `a loved world cannot be outranked off the page by untouched ones`() {
        // Nine untouched worlds win every freshness/exploration term; the tenth holds the only
        // tracks the listener demonstrably loves and must still make the row.
        val lovedTracks = (1..3).map { track("loved$it", artist = "Loved$it") }
        val fillerWorlds = (1..9).map { index ->
            LibraryWorld(
                name = "Filler $index",
                tracks = (1..4).map { track("f$index-$it", artist = "F$index-$it") },
            )
        }
        val lovedWorld = LibraryWorld(name = "Home", tracks = lovedTracks)
        val stats = lovedTracks.associate {
            it.id to stats(plays = 12, completions = 11, last = now - 2 * day)
        }
        val library = fillerWorlds.flatMap { it.tracks } + lovedTracks
        val result = ForYouBuilder.build(
            library = library,
            stats = stats,
            recentEvents = emptyList(),
            nowMs = now,
            worlds = fillerWorlds + lovedWorld,
        )
        val shown = result.sections.first { it.kind == ForYouSectionKind.WORLDS }
            .cards.mapNotNull { it.collection?.title }
        assertTrue("Home" in shown, "the listener's own region fell off the page: $shown")
    }

    @Test
    fun `loved members anchor their mix even when exploration outranks them`() {
        val loved = track("loved", artist = "Loved")
        val fresh = (1..40).map { track("fresh$it", artist = "Fresh$it") }
        val world = LibraryWorld(name = "Region", tracks = fresh + loved)
        val result = ForYouBuilder.build(
            library = fresh + loved,
            stats = mapOf(loved.id to stats(plays = 9, completions = 9, last = now - 40 * day)),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(world),
        )
        val mix = result.sections.first { it.kind == ForYouSectionKind.WORLDS }
            .cards.single().collection
        assertTrue(
            mix?.tracks?.any { it.id == loved.id } == true,
            "a demonstrably loved member fell out of its own region's mix",
        )
    }

    @Test
    fun `never played walks its strangers before the cooling tail`() {
        val strangers = (1..8).map { track("new$it", artist = "New$it", added = 500) }
        val cooledTracks = (1..8).map { track("cooled$it", artist = "Cooled$it", added = 900) }
        val cooled = cooledTracks.mapTo(HashSet()) { it.id }
        val result = ForYouBuilder.build(
            library = strangers + cooledTracks,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            cooledDiscoveries = cooled,
            // Day fourteen lands on the last slot of the fifteen-track pool the row walks — the
            // first slot of the cooled tail — so the walk used to open on yesterday's repeat
            // while eight strangers sat at the front of the same list.
            localDayOf = { 14L },
        )

        val shown = result.sections
            .single { it.kind == ForYouSectionKind.NEVER_PLAYED }
            .cards.map { it.track.id }
        val strangerCount = shown.count { it !in cooled }
        val firstCooled = shown.indexOfFirst { it in cooled }
        assertTrue(strangerCount > 0, "the row offered nobody new: ${shown.map { it.value }}")
        assertTrue(
            firstCooled == -1 || firstCooled == strangerCount,
            "a cooled repeat was shown ahead of a stranger: ${shown.map { it.value }}",
        )
    }

    @Test
    fun `a fresh daypart slot rotates strangers ahead of the cooling tail`() {
        val proven = (1..3).map { track("proven$it", artist = "Proven$it") }
        val cooled = track("cooled", artist = "Cooled")
        val strangers = (1..5).map { track("new$it", artist = "New$it") }
        val members = proven + listOf(cooled) + strangers
        val stats = proven.associate { it.id to stats(plays = 5, last = now - day) }
        val affinity = proven.associate {
            it.id to ForYouRhythm.DaypartAffinity(weight = 4, plays = 4)
        }

        // The cooled track is the last entry of the pool, and this day index opened the walk
        // exactly there — yesterday's repeat took the first fresh slot.
        val row = ForYouRhythm.daypartRow(
            affinity = affinity,
            byId = members.associateBy { it.id },
            worlds = listOf(LibraryWorld("Region", members)),
            stats = stats,
            used = emptySet(),
            dayIndex = members.size - 1,
            cooled = setOf(cooled.id),
        )
        val fresh = row.filter { (stats[it.id]?.plays ?: 0) == 0 }
        assertEquals(ForYouRhythm.DAYPART_FRESH_SLOTS, fresh.size)
        assertTrue(
            fresh.none { it.id == cooled.id },
            "a cooled repeat took a fresh slot: ${fresh.map { it.id.value }}",
        )

        // With fewer strangers than slots the tail still fills the row, and the stranger goes
        // first: an empty slot helps nobody.
        val thin = proven + listOf(cooled) + strangers.take(1)
        val thinFresh = ForYouRhythm.daypartRow(
            affinity = affinity,
            byId = thin.associateBy { it.id },
            worlds = listOf(LibraryWorld("Region", thin)),
            stats = stats,
            used = emptySet(),
            dayIndex = 0,
            cooled = setOf(cooled.id),
        ).filter { (stats[it.id]?.plays ?: 0) == 0 }
        assertEquals(listOf(strangers.first().id, cooled.id), thinFresh.map { it.id })
    }

    @Test
    fun `the wildcard slot prefers a stranger to a repeat`() {
        val anchor = track("anchor", artist = "Anchor")
        val cooled = track("cooled", artist = "Cooled")
        val strangers = (1..4).map { track("new$it", artist = "New$it") }
        val world = LibraryWorld("Dormant", listOf(anchor, cooled) + strangers)
        val stats = mapOf(anchor.id to stats(plays = 9, last = now - 30 * day))

        // The cooled track sits last in the candidate pool, and this day index opened the walk
        // exactly there — the serendipity slot went to a repeat.
        val pick = ForYouRhythm.wildcard(
            worlds = listOf(world),
            stats = stats,
            nowMs = now,
            used = emptySet(),
            dayIndex = 4,
            cooled = setOf(cooled.id),
        )
        assertEquals(strangers.first().id, pick?.pick?.id)
    }

    @Test
    fun `the hero yields to a stranger once its whole unheard pool has been offered`() {
        val offered = (1..5).map { track("offered$it", artist = "Offered$it", added = 900L - it) }
        val stranger = track("stranger", artist = "Stranger", added = 1)
        val page = ForYouBuilder.build(
            library = offered + stranger,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            cooledDiscoveries = offered.mapTo(HashSet()) { it.id },
            localDayOf = utcDay,
        )

        // The hero is the page's one unheard card and its show is written to the impression
        // journal like any other, so the newest-unheard pool is the wrong pool when all of it is
        // cooling: the card used to be the same handful of tracks day after day, each show
        // recorded as if it were a new offer.
        assertEquals(stranger.id, page.hero?.track?.id)
        assertEquals(ForYouKicker.NeverPlayed, page.hero?.kicker)
    }

    @Test
    fun `the on a roll row walks its strangers before the cooling tail`() {
        val played = track("played", artist = "Kanno")
        // The cooled cut leads the fit order, so the row used to open on yesterday's repeat while
        // the artist's unoffered cuts waited behind it.
        val cooled = track("a-cooled", artist = "Kanno")
        val strangers = (1..3).map { track("b-new$it", artist = "Kanno") }
        // The hero claims the newest unheard track; it is not by the binged artist, so it cannot
        // stand in for one of the cuts under test.
        val bait = track("bait", artist = "Bait", added = 900)
        // The radio row claims one unheard start. This spare is that start, so the binge cuts
        // stay in the row under test.
        val spare = track("0-spare", artist = "Spare")
        val page = ForYouBuilder.build(
            library = listOf(played, cooled) + strangers + bait + spare,
            stats = mapOf(played.id to stats(plays = 12, last = now - day)),
            // Twelve plays in the phase window and none of this artist before it: the strongest
            // binge signal, over a log old enough (a month) for a "phase" to mean something.
            recentEvents = (1..12).map { n ->
                ListenEvent(
                    trackId = played.id,
                    startedAtMs = now - (n % 6) * day - n * hour,
                    playedMs = 60_000,
                    trackDurationMs = 120_000,
                    completed = true,
                    skipped = false,
                )
            } + ListenEvent(
                trackId = TrackId("long-gone"),
                startedAtMs = now - 30 * day,
                playedMs = 60_000,
                trackDurationMs = 120_000,
                completed = true,
                skipped = false,
            ),
            nowMs = now,
            cooledDiscoveries = setOf(cooled.id),
            localDayOf = { 0L },
        )

        val shown = page.sections
            .single { it.kind == ForYouSectionKind.ON_A_ROLL }
            .cards.map { it.track.id }
        val newcomerCount = shown.count { it != cooled.id }
        val firstCooled = shown.indexOfFirst { it == cooled.id }
        assertTrue(newcomerCount > 0, "the row offered nobody new: ${shown.map { it.value }}")
        assertTrue(
            firstCooled == newcomerCount,
            "a cooled repeat was shown ahead of a stranger: ${shown.map { it.value }}",
        )
    }

    @Test
    fun `a heavy listener with an old log gets the on a roll row from its real start`() {
        val played = track("played", artist = "Kanno")
        val cuts = (1..3).map { track("cut$it", artist = "Kanno") }
        val bait = track("bait", artist = "Bait", added = 900)
        val spare = track("0-spare", artist = "Spare")
        // Everything the listener plays was played this week, and the recent events reach back
        // only days: neither says that the log is a year old.
        fun page(logStartMs: Long?) = ForYouBuilder.build(
            library = listOf(played) + cuts + bait + spare,
            stats = mapOf(played.id to stats(plays = 12, last = now - day)),
            recentEvents = (1..12).map { n ->
                ListenEvent(
                    trackId = played.id,
                    startedAtMs = now - (n % 6) * day - n * hour,
                    playedMs = 60_000,
                    trackDurationMs = 120_000,
                    completed = true,
                    skipped = false,
                )
            },
            nowMs = now,
            localDayOf = { 0L },
            logStartMs = logStartMs,
        )

        assertTrue(page(logStartMs = null).sections.none { it.kind == ForYouSectionKind.ON_A_ROLL })
        assertTrue(page(logStartMs = now - 365 * day).sections.any { it.kind == ForYouSectionKind.ON_A_ROLL })
    }

    @Test
    fun `a favourite offered yesterday and not taken steps aside for the next one`() {
        val tracks = (1..4).map { track("$it", artist = "Artist$it") }
        // Track 1 has the most completions, so it leads the hero's pool.
        val stats = tracks.withIndex().associate { (index, t) ->
            t.id to stats(plays = 10 - index, last = now - 200 * day)
        }
        val today = utcDay(now)
        fun hero(shown: Map<String, Long>) = ForYouBuilder.build(
            tracks, stats, emptyList(), now, localDayOf = utcDay, revisitShownDays = shown,
        ).hero?.track?.id?.value
        assertEquals("1", hero(emptyMap()))
        assertEquals("2", hero(mapOf("1" to today - 1)))
        // A week later it has had its rest and leads again.
        assertEquals("1", hero(mapOf("1" to today - 8)))
    }

    @Test
    fun `worth revisiting moves an ignored card behind the rest`() {
        val tracks = (1..7).map { track("$it", artist = "Artist$it") }
        val stats = tracks.withIndex().associate { (index, t) ->
            t.id to stats(plays = 20 - index, last = now - 200 * day)
        }
        val today = utcDay(now)
        fun row(shown: Map<String, Long>) = ForYouBuilder.build(
            tracks, stats, emptyList(), now, localDayOf = utcDay, revisitShownDays = shown,
        ).sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }.cards.map { it.track.id.value }
        val before = row(emptyMap())
        val lead = before.first()
        val after = row(mapOf(lead to today - 1))
        assertTrue(after.first() != lead, "an ignored offer kept the lead: $after")
        assertEquals(lead, after.last())
    }

    @Test
    fun `never played leads with tracks added this month`() {
        val old = (1..5).map { track("old$it", artist = "Old$it", added = now - (400 + it) * day) }
        val recent = track("recent", artist = "New", added = now - 3 * day)
        // Radio claims one unheard start. This one sorts first, so the recent addition stays
        // at the head of never played.
        val spare = track("aaa", artist = "Spare")
        val result = ForYouBuilder.build(
            old + recent + spare, emptyMap(), emptyList(), now, localDayOf = utcDay,
            // Offered yesterday and ignored: a plain cooled track steps back, a recent addition
            // still leads the row.
            cooledDiscoveries = setOf(recent.id),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.NEVER_PLAYED }
        assertEquals("recent", row.cards.first().track.id.value)
    }

    @Test
    fun `a mass add date in the last month is not mistaken for new music`() {
        // A migration stamped the whole library three days ago: "recently added" means nothing.
        val stamped = (1..6).map { track("t$it", artist = "A$it", added = now - 3 * day) }
        assertTrue(!ForYouBuilder.addDatesTrusted(stamped, now, utcDay))
        // Copied long ago, then one album added last week: that addition is real.
        val copiedLongAgo = (1..6).map { track("t$it", artist = "A$it", added = now - 300 * day) } +
            track("new", artist = "N", added = now - 7 * day)
        assertTrue(ForYouBuilder.addDatesTrusted(copiedLongAgo, now, utcDay))
    }

    @Test
    fun `a retired unheard track stays off never played`() {
        val tracks = (1..4).map { track("$it", artist = "Artist$it", added = it.toLong()) }
        val result = ForYouBuilder.build(
            tracks, emptyMap(), emptyList(), now, localDayOf = utcDay,
            retiredDiscoveries = setOf(TrackId("4")),
        )
        val shown = result.sections.first { it.kind == ForYouSectionKind.NEVER_PLAYED }
            .cards.map { it.track.id.value } + listOfNotNull(result.hero?.track?.id?.value)
        assertTrue("4" !in result.sections.first { it.kind == ForYouSectionKind.NEVER_PLAYED }
            .cards.map { it.track.id.value }, "retired track offered: $shown")
    }

    @Test
    fun `albums of two artists with one title are two albums`() {
        val a = (1..3).map { track("a$it", artist = "A", album = "Greatest Hits") }
        val b = (1..3).map { track("b$it", artist = "B", album = "Greatest Hits") }
        val bait = track("bait", artist = "Z")
        val all = a + b + bait
        val result = page(
            library = all,
            stats = all.associate { it.id to stats(plays = 6, last = now - 200 * day) } +
                // The hero takes the strongest single; the bait is it, so both albums keep three.
                (bait.id to stats(plays = 50, last = now - 200 * day)),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        val keys = row.cards.mapNotNull { it.collection?.key }.sorted()
        assertEquals(listOf("collection:Greatest Hits|a", "collection:Greatest Hits|b"), keys)
    }

    @Test
    fun `a catch-all playlist is never offered whole`() {
        val tracks = (1..30).map { track("$it", artist = "Artist$it") }
        val result = page(
            library = tracks,
            stats = tracks.associate { it.id to stats(plays = 6, last = now - 200 * day) },
            playlists = listOf(Playlist(id = "all", name = "All music", trackIds = tracks.map { it.id.value })),
        )
        val row = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
        assertTrue(row.cards.none { it.collection?.playlistId == "all" })
    }

    @Test
    fun `a collection card is recorded under its own key`() {
        val members = (1..4).map { track("$it", artist = "Artist$it") }
        val result = page(
            library = members,
            stats = members.associate { it.id to stats(plays = 6, last = now - 200 * day) },
            playlists = listOf(Playlist(id = "p1", name = "Think", trackIds = members.map { it.id.value })),
        )
        val card = result.sections.first { it.kind == ForYouSectionKind.WORTH_REVISITING }
            .cards.first { it.collection != null }
        assertEquals("collection:p1", card.impressionKey)
    }

    @Test
    fun `a generic mix keeps its number from day to day`() {
        val worlds = (1..7).map { w ->
            LibraryWorld(
                "ignored $w",
                (1..4).map { track("w$w-$it", artist = "W$w-$it") },
                LibraryWorldNameSource.GENERIC,
            )
        }
        fun titles(dayKey: Long) = ForYouBuilder.build(
            library = worlds.flatMap { it.tracks },
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = worlds,
            discoveryMixLabel = "Mix",
            localDayOf = { dayKey },
        ).sections.first { it.kind == ForYouSectionKind.WORLDS }
            .cards.associate { it.impressionKey to it.collection?.title }
        val monday = titles(100)
        val tuesday = titles(101)
        assertTrue(monday.keys.intersect(tuesday.keys).isNotEmpty())
        for (key in monday.keys.intersect(tuesday.keys)) {
            assertEquals(monday[key], tuesday[key], "mix $key was renumbered overnight")
        }
    }

    @Test
    fun `when every favourite was offered recently they take turns`() {
        val tracks = (1..2).map { track("$it", artist = "Artist$it") }
        val stats = tracks.withIndex().associate { (index, t) ->
            t.id to stats(plays = 10 - index, last = now - 200 * day)
        }
        val today = utcDay(now)
        fun hero(shown: Map<String, Long>) = ForYouBuilder.build(
            tracks, stats, emptyList(), now, localDayOf = utcDay, revisitShownDays = shown,
        ).hero?.track?.id?.value
        // Both were offered and ignored: the one that has waited longest leads, so a small pool
        // alternates instead of the strongest favourite returning every other day.
        assertEquals("1", hero(mapOf("1" to today - 2, "2" to today - 1)))
        assertEquals("2", hero(mapOf("1" to today - 1, "2" to today - 2)))
    }

    @Test
    fun `an unheard song is enough to show the radio row`() {
        val loved = track("loved")
        val fresh = track("fresh", artist = "B")
        val result = page(
            library = listOf(loved, fresh),
            stats = mapOf(loved.id to stats(plays = 8, last = now - 200 * day)),
        )
        val radio = result.sections.first { it.kind == ForYouSectionKind.RADIO }
        assertEquals(listOf(fresh.id), radio.cards.map { it.track.id })
        assertEquals(ForYouCaption.NeverPlayed, radio.cards.single().caption)
        assertTrue(result.sections.none { it.kind == ForYouSectionKind.NEVER_PLAYED })
    }

    @Test
    fun `played starts stay hidden until three of them line up`() {
        val loved = (1..2).map { track("q$it", artist = "Quiet$it") }
        val result = sections(
            library = loved,
            stats = loved.associate { it.id to stats(plays = 8, last = now - 200 * day) },
        )
        assertTrue(result.none { it.kind == ForYouSectionKind.RADIO })
    }

    @Test
    fun `a large unheard library offers a scroll of radio starts`() {
        val library = (1..40).map { track("u%02d".format(it), artist = "Artist$it") }
        fun built(dayKey: Long) = ForYouBuilder.build(
            library = library,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            localDayOf = { dayKey },
        )
        val today = built(0)
        val radio = today.sections.first { it.kind == ForYouSectionKind.RADIO }
        // 40 unheard tracks, one claimed by the hero, eight held for never-played: the spare
        // is under two full windows, so the row shows half of it.
        assertEquals(15, radio.cards.size)
        assertTrue(radio.cards.all { it.caption == ForYouCaption.NeverPlayed })
        assertEquals(radio.cards.map { it.track.id }.distinct().size, radio.cards.size)
        assertTrue(radio.cards.none { it.track.id == today.hero?.track?.id })
        assertEquals(radio.cards, built(0).sections.first { it.kind == ForYouSectionKind.RADIO }.cards)
        val neverPlayed = today.sections.first { it.kind == ForYouSectionKind.NEVER_PLAYED }
        assertEquals(ForYouBuilder.ROW_LIMIT, neverPlayed.cards.size)
        val radioIds = radio.cards.map { it.track.id }.toSet()
        assertTrue(neverPlayed.cards.none { it.track.id in radioIds })
        val tomorrow = built(1).sections.first { it.kind == ForYouSectionKind.RADIO }.cards
        assertTrue(radioIds.intersect(tomorrow.map { it.track.id }.toSet()).isEmpty())
    }

    @Test
    fun `a radio refresh jumps to a different set of starts`() {
        // The played favourite keeps the hero off the unheard pool, so a new day and a refresh
        // are the same step over the same 57 songs.
        val fresh = (1..57).map { track("u%02d".format(it), artist = "Artist$it") }
        val loved = track("loved", artist = "Loved")
        fun built(dayKey: Long, shift: Int) = ForYouBuilder.build(
            library = listOf(loved) + fresh,
            stats = mapOf(loved.id to stats(plays = 12, last = now - 200 * day)),
            recentEvents = emptyList(),
            nowMs = now,
            localDayOf = { dayKey },
            radioShift = shift,
        )
        fun ids(dayKey: Long, shift: Int) =
            built(dayKey, shift).sections.first { it.kind == ForYouSectionKind.RADIO }.cards.map { it.track.id }
        val today = ids(0, 0)
        val refreshed = ids(0, 1)
        assertEquals(ForYouBuilder.RADIO_CHOICES, today.size)
        assertEquals(today.size, refreshed.size)
        assertTrue(today.toSet().intersect(refreshed.toSet()).isEmpty())
        assertEquals(today, ids(0, 0))
        assertEquals(refreshed, ids(1, 0))
    }

    @Test
    fun `a medium unheard pool replaces its radio starts on refresh`() {
        val library = (1..30).map { track("u%02d".format(it), artist = "Artist$it") }
        fun ids(shift: Int) = ForYouBuilder.build(
            library = library,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            localDayOf = { 0L },
            radioShift = shift,
        ).sections.first { it.kind == ForYouSectionKind.RADIO }.cards.map { it.track.id }
        val today = ids(0)
        val refreshed = ids(1)
        assertEquals(10, today.size)
        assertEquals(today.size, refreshed.size)
        assertTrue(today.all { it != library.first().id })
        assertTrue(today.toSet().intersect(refreshed.toSet()).isEmpty())
    }

    @Test
    fun `a rested radio favourite takes its turn with the day and the refresh`() {
        val loved = listOf(
            track("q1", artist = "Quiet1"),
            track("q2", artist = "Quiet2"),
            track("q3", artist = "Quiet3"),
        )
        val fresh = track("n1", artist = "New")
        val stats = loved.withIndex().associate { (index, track) ->
            track.id to stats(plays = 12 - index * 3, last = now - 200 * day)
        }
        fun quietId(dayKey: Long, shift: Int) = ForYouBuilder.build(
            library = loved + fresh,
            stats = stats,
            recentEvents = emptyList(),
            nowMs = now,
            localDayOf = { dayKey },
            radioShift = shift,
        ).sections.first { it.kind == ForYouSectionKind.RADIO }
            .cards.first { it.caption is ForYouCaption.PlayedBefore }
            .track.id.value
        assertEquals("q2", quietId(0, 0))
        assertEquals("q3", quietId(0, 1))
        assertEquals(quietId(0, 1), quietId(1, 0))
        assertEquals("q2", quietId(0, 2))
    }

    @Test
    fun `the mix radio start moves to the next world`() {
        val loved = track("loved")
        val fresh = track("fresh", artist = "Fresh")
        val jazz = (1..3).map { track("a$it", artist = "Jazz$it") }
        val soul = (1..3).map { track("b$it", artist = "Soul$it") }
        val library = listOf(loved, fresh) + jazz + soul
        val stats = mapOf(loved.id to stats(plays = 12, last = now - 200 * day)) +
            (jazz + soul).associate { it.id to stats(plays = 1, last = now - day) }
        val two = listOf(LibraryWorld("Jazz", jazz), LibraryWorld("Soul", soul))
        fun worldCard(worlds: List<LibraryWorld>, dayKey: Long, shift: Int) = ForYouBuilder.build(
            library = library,
            stats = stats,
            recentEvents = emptyList(),
            nowMs = now,
            worlds = worlds,
            localDayOf = { dayKey },
            radioShift = shift,
        ).sections.first { it.kind == ForYouSectionKind.RADIO }
            .cards.first { it.caption is ForYouCaption.WorldName }
        val today = worldCard(two, 0, 0)
        val refreshed = worldCard(two, 0, 1)
        assertEquals("a1", today.track.id.value)
        assertEquals(ForYouCaption.WorldName("Jazz"), today.caption)
        assertEquals("b1", refreshed.track.id.value)
        assertEquals(ForYouCaption.WorldName("Soul"), refreshed.caption)
        assertEquals(refreshed, worldCard(two, 1, 0))
        val onlyJazz = worldCard(listOf(LibraryWorld("Jazz", jazz)), 0, 1)
        assertEquals("a1", onlyJazz.track.id.value)
        assertEquals(ForYouCaption.WorldName("Jazz"), onlyJazz.caption)
    }

    @Test
    fun `extra radio starts do not let one artist fill the row`() {
        val library = (1..30).map { track("a%02d".format(it), artist = "Same") } +
            (1..30).map { track("b%02d".format(it), artist = "Other$it") }
        val radio = ForYouBuilder.build(
            library = library,
            stats = emptyMap(),
            recentEvents = emptyList(),
            nowMs = now,
            localDayOf = { 0L },
        ).sections.first { it.kind == ForYouSectionKind.RADIO }
        assertEquals(ForYouBuilder.RADIO_CHOICES, radio.cards.size)
        val perArtist = radio.cards.groupingBy { it.track.artist }.eachCount()
        assertTrue(perArtist.values.all { it <= ForYouBuilder.MAX_PER_ARTIST })
        assertTrue((perArtist["Same"] ?: 0) <= ForYouBuilder.MAX_PER_ARTIST)
    }

    @Test
    fun `a radio start skips the hero and leaves never played on a short page`() {
        val page = radioPage(dayKey = 0)
        val radio = page.sections.first { it.kind == ForYouSectionKind.RADIO }
        assertEquals(page.hero?.track?.id, track("q-strong").id)
        assertTrue(radio.cards.none { it.track.id == page.hero?.track?.id })
        assertEquals(
            listOf("q-next", "a-new", "w1"),
            radio.cards.map { it.track.id.value },
        )
        assertEquals(ForYouCaption.PlayedBefore(8), radio.cards[0].caption)
        assertEquals(ForYouCaption.NeverPlayed, radio.cards[1].caption)
        assertEquals(ForYouCaption.WorldName("Jazz"), radio.cards[2].caption)
        assertTrue(page.sections.any { it.kind == ForYouSectionKind.NEVER_PLAYED })
        assertTrue(page.sections.size <= ForYouBuilder.MAX_SECTIONS)
    }

    @Test
    fun `radio picks hold for a local day and the unheard start moves the next day`() {
        val today = radioPage(dayKey = 0)
        val again = radioPage(dayKey = 0)
        val tomorrow = radioPage(dayKey = 1)
        val todayRadio = today.sections.first { it.kind == ForYouSectionKind.RADIO }.cards
        assertEquals(todayRadio, again.sections.first { it.kind == ForYouSectionKind.RADIO }.cards)
        val tomorrowRadio = tomorrow.sections.first { it.kind == ForYouSectionKind.RADIO }.cards
        assertEquals(todayRadio[0], tomorrowRadio[0])
        assertEquals(todayRadio[2], tomorrowRadio[2])
        assertEquals("b-new", tomorrowRadio[1].track.id.value)
    }

    @Test
    fun `a generic world name is not a radio caption`() {
        val loved = track("loved")
        val next = track("next", artist = "Next")
        val fresh = track("fresh", artist = "Fresh")
        val members = (1..3).map { track("g$it", artist = "Region$it") }
        val page = ForYouBuilder.build(
            library = listOf(loved, next, fresh) + members,
            stats = mapOf(
                loved.id to stats(plays = 9, last = now - 200 * day),
                next.id to stats(plays = 6, last = now - 200 * day),
            ),
            recentEvents = emptyList(),
            nowMs = now,
            worlds = listOf(LibraryWorld("Discovery mix", members, LibraryWorldNameSource.GENERIC)),
            localDayOf = { 0L },
        )
        val radio = page.sections.first { it.kind == ForYouSectionKind.RADIO }
        assertNull(radio.cards.first { it.track.id == members.first().id }.caption)
    }

    @Test
    fun `an excluded track is not offered as a radio start`() {
        val loved = track("loved")
        val blocked = track("blocked", artist = "Blocked")
        val fresh = track("fresh", artist = "Fresh")
        val members = (1..3).map { track("m$it", artist = "Region$it") }
        val page = ForYouBuilder.build(
            library = listOf(loved, blocked, fresh) + members,
            stats = mapOf(
                loved.id to stats(plays = 9, last = now - 200 * day),
                blocked.id to stats(plays = 8, last = now - 200 * day),
            ),
            recentEvents = emptyList(),
            nowMs = now,
            excluded = setOf(blocked.id),
            worlds = listOf(LibraryWorld("Jazz", members)),
            localDayOf = { 0L },
        )
        assertTrue(page.sections.flatMap { it.cards }.none { it.track.id == blocked.id })
        assertTrue(page.hero?.track?.id != blocked.id)
    }

    @Test
    fun `calmer and brighter appear only when the sound model has a clear winner`() {
        val library = energyLibrary()
        val without = energyPage(library, emptyMap())
        assertTrue(
            without.cards.none { it.caption == ForYouCaption.CalmerGuess || it.caption == ForYouCaption.BrighterGuess },
        )
        val flat = energyPage(library, energyScores(low = 0.5f, high = 0.5f))
        assertTrue(
            flat.cards.none { it.caption == ForYouCaption.CalmerGuess || it.caption == ForYouCaption.BrighterGuess },
        )
        val clear = energyPage(
            library,
            mapOf(
                TrackId("e-low") to energyRow(low = 0.9f, high = 0.1f),
                TrackId("e-high") to energyRow(low = 0.1f, high = 0.9f),
                TrackId("e-mid") to energyRow(low = 0.4f, high = 0.4f),
                TrackId("e-speech") to energyRow(low = 0.99f, high = 0.99f, music = 0.2f, speech = 0.9f),
                TrackId("e-other") to energyRow(low = 0.3f, high = 0.3f),
            ),
        )
        assertEquals("e-low", clear.cards.first { it.caption == ForYouCaption.CalmerGuess }.track.id.value)
        assertEquals("e-high", clear.cards.first { it.caption == ForYouCaption.BrighterGuess }.track.id.value)
        assertEquals(5, clear.cards.size)
    }

    @Test
    fun `never played yields only when the radio row would make a seventh section`() {
        val kept = ForYouSectionKind.entries.filterNot { it == ForYouSectionKind.NEVER_PLAYED }
        val seven = ForYouSectionKind.entries.map { ForYouSection(it, emptyList()) }
        with(ForYouBuilder) {
            assertEquals(kept, seven.capped().map { it.kind })
            assertEquals(kept, kept.map { ForYouSection(it, emptyList()) }.capped().map { it.kind })
            val sixWithNeverPlayed = seven.drop(1)
            assertEquals(sixWithNeverPlayed, sixWithNeverPlayed.capped())
        }

        val full = busyPage(includeWorld = true)
        assertEquals(
            listOf(
                ForYouSectionKind.RADIO,
                ForYouSectionKind.CONTINUE,
                ForYouSectionKind.DAYPART,
                ForYouSectionKind.WORTH_REVISITING,
                ForYouSectionKind.ON_A_ROLL,
                ForYouSectionKind.WORLDS,
            ),
            full.sections.map { it.kind },
            "sections were ${full.sections.map { it.kind }}",
        )
        val withoutWorld = busyPage(includeWorld = false)
        assertTrue(withoutWorld.sections.any { it.kind == ForYouSectionKind.NEVER_PLAYED })
        assertTrue(withoutWorld.sections.any { it.kind == ForYouSectionKind.RADIO })
    }

    private fun radioPage(dayKey: Long) = ForYouBuilder.build(
        library = listOf(
            track("q-strong"),
            track("q-next", artist = "Next"),
            track("a-new", artist = "NewA"),
            track("b-new", artist = "NewB"),
        ) + (1..3).map { track("w$it", artist = "Region$it") },
        stats = mapOf(
            TrackId("q-strong") to stats(plays = 12, last = now - 200 * day),
            TrackId("q-next") to stats(plays = 8, last = now - 200 * day),
        ),
        recentEvents = emptyList(),
        nowMs = now,
        worlds = listOf(LibraryWorld("Jazz", (1..3).map { track("w$it", artist = "Region$it") })),
        localDayOf = { dayKey },
    )

    private fun energyLibrary(): List<TrackDescriptor> = listOf(
        track("loved"),
        track("next", artist = "Next"),
        track("fresh", artist = "Fresh"),
        track("e-low", artist = "Low"),
        track("e-high", artist = "High"),
        track("e-mid", artist = "Mid"),
        track("e-speech", artist = "Speech"),
        track("e-other", artist = "Other"),
    ) + (1..3).map { track("w$it", artist = "Region$it") }

    private fun energyPage(
        library: List<TrackDescriptor>,
        semantics: Map<TrackId, TrackSemantics>,
    ) = ForYouBuilder.build(
        library = library,
        stats = mapOf(
            TrackId("loved") to stats(plays = 9, last = now - 200 * day),
            TrackId("next") to stats(plays = 6, last = now - 200 * day),
            TrackId("e-low") to stats(plays = 1, last = now - day),
            TrackId("e-high") to stats(plays = 1, last = now - day),
            TrackId("e-mid") to stats(plays = 1, last = now - day),
            TrackId("e-speech") to stats(plays = 1, last = now - day),
            TrackId("e-other") to stats(plays = 1, last = now - day),
        ),
        recentEvents = emptyList(),
        nowMs = now,
        worlds = listOf(LibraryWorld("Jazz", (1..3).map { track("w$it", artist = "Region$it") })),
        localDayOf = { 0L },
        semantics = semantics,
    ).sections.first { it.kind == ForYouSectionKind.RADIO }

    private fun energyScores(low: Float, high: Float) = listOf("e-low", "e-high", "e-mid", "e-other")
        .associate { TrackId(it) to energyRow(low, high) }

    private fun energyRow(
        low: Float,
        high: Float,
        music: Float = 0.8f,
        speech: Float = 0.1f,
        effects: Float = 0.1f,
    ): TrackSemantics {
        val scores = FloatArray(TrackSemantics.OUTPUT_SIZE)
        scores[SemanticLabel.MUSIC.modelIndex] = music
        scores[SemanticLabel.SPEECH.modelIndex] = speech
        scores[SemanticLabel.SOUND_EFFECTS.modelIndex] = effects
        scores[SemanticLabel.ENERGY_LOW.modelIndex] = low
        scores[SemanticLabel.ENERGY_HIGH.modelIndex] = high
        return checkNotNull(TrackSemantics.fromModelOutput(scores))
    }

    private fun busyPage(includeWorld: Boolean): ForYouPage {
        val quiet = (1..5).map { index ->
            track("q$index", artist = "Quiet$index") to stats(plays = 12 - index, last = now - 200 * day)
        }
        val dawn = (1..5).map { track("d$it", artist = "Dawn$it") }
        val resume = listOf(track("r1", artist = "Resume"), track("r2", artist = "ResumeTwo"))
        val played = track("k-played", artist = "Kanno")
        val cuts = (1..4).map { track("k$it", artist = "Kanno") }
        val fresh = listOf(track("0radio", artist = "Radio"), track("n1", artist = "New1"), track("n2", artist = "New2"))
        val region = (1..3).map { track("w$it", artist = "Region$it") }
        val library = quiet.map { it.first } + dawn + resume + played + cuts + fresh + region
        val stats = quiet.associate { (descriptor, stat) -> descriptor.id to stat } +
            dawn.associate { it.id to stats(plays = 5, last = now - 2 * day) } +
            resume.associate { it.id to stats(plays = 1, last = now - hour) } +
            mapOf(played.id to stats(plays = 12, last = now - hour))
        fun event(id: TrackId, at: Long, done: Boolean, playedMs: Long, durationMs: Long) = ListenEvent(
            trackId = id,
            startedAtMs = at,
            playedMs = playedMs,
            trackDurationMs = durationMs,
            completed = done,
            skipped = false,
        )
        val events = listOf(
            event(resume[0].id, now - hour, done = false, playedMs = 90_000, durationMs = 600_000),
            event(resume[1].id, now - 2 * hour, done = false, playedMs = 90_000, durationMs = 600_000),
        ) + dawn.flatMapIndexed { index, track ->
            (1..5).map { n ->
                event(track.id, now - 2 * day - (index * 5L + n) * hour, done = true, playedMs = 120_000, durationMs = 120_000)
            }
        } + (1..12).map { n ->
            event(played.id, now - (n % 6) * day - n * hour, done = true, playedMs = 120_000, durationMs = 120_000)
        }
        return ForYouBuilder.build(
            library = library,
            stats = stats,
            recentEvents = events,
            nowMs = now,
            worlds = if (includeWorld) listOf(LibraryWorld("Jazz", region)) else emptyList(),
            localDayOf = { 0L },
            localHourOf = { 9 },
            logStartMs = now - 365 * day,
        )
    }
}
