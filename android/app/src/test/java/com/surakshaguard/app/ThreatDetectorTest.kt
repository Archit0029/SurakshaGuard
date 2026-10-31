package com.surakshaguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that ThreatDetector:
 * 1. Has ZERO false positives on clean devices.
 * 2. Only flags genuine threats (active call, active remote screen share, demo mode).
 * 3. Does not block when user is clean.
 */
class ThreatDetectorTest {

    @Test
    fun testCleanEnvironment_returnsNoThreat() {
        val report = ThreatDetector.evaluate(
            isDemoMode = false,
            hasActiveCall = false,
            activeRemoteApp = null,
            isMirroringActive = false
        )

        assertEquals("Clean environment must return ThreatType.NONE", ThreatType.NONE, report.type)
        assertNull("No offending package should be reported", report.offendingPackage)
        assertNull("No offending app name should be reported", report.offendingAppName)
    }

    @Test
    fun testActiveCall_returnsActivePhoneCallThreat() {
        val report = ThreatDetector.evaluate(
            isDemoMode = false,
            hasActiveCall = true,
            activeRemoteApp = null,
            isMirroringActive = false
        )

        assertEquals(ThreatType.ACTIVE_PHONE_CALL, report.type)
        assertTrue(report.title.contains("Active Phone Call"))
        assertTrue(report.titleHindi.contains("फोन कॉल"))
    }

    @Test
    fun testActiveRemoteApp_returnsScreenShareThreatWithPackage() {
        val sampleApp = RemoteAppInfo("com.anydesk.anydeskandroid", "AnyDesk Remote Desktop")
        val report = ThreatDetector.evaluate(
            isDemoMode = false,
            hasActiveCall = false,
            activeRemoteApp = sampleApp,
            isMirroringActive = false
        )

        assertEquals(ThreatType.REMOTE_SCREEN_SHARE, report.type)
        assertEquals("com.anydesk.anydeskandroid", report.offendingPackage)
        assertEquals("AnyDesk Remote Desktop", report.offendingAppName)
        assertTrue(report.description.contains("AnyDesk"))
    }

    @Test
    fun testLiveScreenMirroring_returnsMirroringThreat() {
        val report = ThreatDetector.evaluate(
            isDemoMode = false,
            hasActiveCall = false,
            activeRemoteApp = null,
            isMirroringActive = true
        )

        assertEquals(ThreatType.REMOTE_SCREEN_SHARE, report.type)
        assertEquals("Active Screen Cast", report.offendingAppName)
    }

    @Test
    fun testDemoMode_returnsDemoSimulationThreat() {
        val report = ThreatDetector.evaluate(
            isDemoMode = true,
            hasActiveCall = false,
            activeRemoteApp = null,
            isMirroringActive = false
        )

        assertEquals(ThreatType.DEMO_MODE, report.type)
        assertTrue(report.title.contains("DEMO SIMULATION"))
    }

    @Test
    fun testPriorityOrder_demoModeTakesPrecedence() {
        val sampleApp = RemoteAppInfo("com.anydesk.anydeskandroid", "AnyDesk Remote Desktop")
        val report = ThreatDetector.evaluate(
            isDemoMode = true,
            hasActiveCall = true,
            activeRemoteApp = sampleApp,
            isMirroringActive = true
        )
        assertEquals(ThreatType.DEMO_MODE, report.type)
    }

    @Test
    fun testPriorityOrder_callTakesPrecedenceOverRemoteApp() {
        val sampleApp = RemoteAppInfo("com.anydesk.anydeskandroid", "AnyDesk Remote Desktop")
        val report = ThreatDetector.evaluate(
            isDemoMode = false,
            hasActiveCall = true,
            activeRemoteApp = sampleApp,
            isMirroringActive = false
        )
        assertEquals(ThreatType.ACTIVE_PHONE_CALL, report.type)
    }

    @Test
    fun testMultiplePermutations_cleanStateAlwaysPasses() {
        // Run clean check 100 times to guarantee consistency
        for (i in 1..100) {
            val report = ThreatDetector.evaluate(
                isDemoMode = false,
                hasActiveCall = false,
                activeRemoteApp = null,
                isMirroringActive = false
            )
            assertEquals("Iteration $i failed clean check", ThreatType.NONE, report.type)
        }
    }
}

