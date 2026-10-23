package com.mindtrack.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationGateTest {
    @Test fun unverifiedEmailAccountsAreHeldAtTheVerificationScreen() {
        assertTrue(requiresEmailVerification(listOf("firebase", "password"), emailVerified = false))
        assertTrue(requiresEmailVerification(listOf("password"), emailVerified = false))
    }

    @Test fun verifiedEmailAccountsGoStraightIn() {
        assertFalse(requiresEmailVerification(listOf("firebase", "password"), emailVerified = true))
    }

    @Test fun googleAccountsAreNeverHeldBack() {
        assertFalse(requiresEmailVerification(listOf("firebase", "google.com"), emailVerified = false))
        assertFalse(requiresEmailVerification(listOf("google.com"), emailVerified = true))
    }

    @Test fun nobodySignedInMeansNothingToVerify() {
        assertFalse(requiresEmailVerification(emptyList(), emailVerified = false))
    }
}
