package com.onyx.app.ui.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAvailabilityTest {
    @Test fun initialLoadingAndUnavailableRoutesNeverPermitSecretWrites() {
        for (state in listOf(VaultAvailability.LOADING, VaultAvailability.UNAVAILABLE)) {
            assertFalse(canWriteVault(state, busy = false))
            assertFalse(canWriteVault(state, busy = true))
        }
    }

    @Test fun successfulReadAllowsWritesOnlyWhileIdle() {
        assertTrue(canWriteVault(VaultAvailability.READY, busy = false))
        assertFalse(canWriteVault(VaultAvailability.READY, busy = true))
    }

    @Test fun retryRevokesPermissionUntilReadCompletes() {
        val reads = listOf(
            VaultAvailability.READY,
            VaultAvailability.LOADING,
            VaultAvailability.UNAVAILABLE,
            VaultAvailability.LOADING,
            VaultAvailability.READY,
        )
        assertTrue(reads.map { canWriteVault(it, busy = false) } == listOf(true, false, false, false, true))
    }
}
