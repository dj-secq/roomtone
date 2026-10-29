/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SeedGravityApplierTest {

    @Test
    fun `scale one is applied at launch and the queued future is kept`() = runTest {
        val setting = Setting(backgroundScope)
        setting.apply(1f)
        settle()
        assertEquals(listOf(1f), setting.scales)
        assertEquals(0, setting.replans)
    }

    @Test
    fun `a new scale replans once and returning to one restores the shipped balance`() = runTest {
        val setting = Setting(backgroundScope)
        setting.apply(1f)
        runCurrent()
        setting.apply(0.5f)
        advanceTimeBy(ARTIST_VARIETY_SETTLE_MS - 1)
        runCurrent()
        assertEquals(0, setting.replans)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, setting.replans)
        assertEquals(0.5f, setting.scales.last())

        setting.apply(1f)
        settle()
        assertEquals(2, setting.replans)
        assertEquals(1f, setting.scales.last())
    }

    private fun TestScope.settle() {
        advanceTimeBy(ARTIST_VARIETY_SETTLE_MS)
        runCurrent()
    }

    private class Setting(private val scope: CoroutineScope) {
        val scales = mutableListOf<Float>()
        var replans = 0
        private var job: Job? = null
        private val applier = SeedGravityApplier(
            setScale = { scales += it },
            invalidateSmartFuture = { replans++ },
        )

        fun apply(scale: Float) {
            job?.cancel()
            job = scope.launch { applier.apply(scale) }
        }
    }
}
