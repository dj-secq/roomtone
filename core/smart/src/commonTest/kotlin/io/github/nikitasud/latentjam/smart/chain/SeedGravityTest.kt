/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.smart.chain

import kotlin.test.Test
import kotlin.test.assertEquals

class SeedGravityTest {

    @Test
    fun `a scale of 1 is the shipped seed gravity`() {
        val tuning = ChainTuning()
        assertEquals(ChainConfig.CHAIN_SEED_GRAVITY, tuning.audioSeedGravity())
        assertEquals(ChainConfig.SEM_CHAIN_SEED_GRAVITY, tuning.semanticSeedGravity())
        assertEquals(ChainConfig.CHAIN_SEED_GRAVITY, ChainTuning(seedGravityScale = 1f).audioSeedGravity())
    }

    @Test
    fun `other scales multiply the shipped gravity and a bad scale does not pull`() {
        assertEquals(2f, ChainTuning(seedGravityScale = 0.5f).audioSeedGravity())
        assertEquals(0f, ChainTuning(seedGravityScale = -1f).audioSeedGravity())
        assertEquals(1.5f, ChainTuning(seedGravityScale = 0.5f).semanticSeedGravity())
    }
}
