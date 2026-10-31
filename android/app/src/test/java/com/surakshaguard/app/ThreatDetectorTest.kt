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
    fun testQr_ReversePaymentScam_flagsFraud() {
        val scamQr = "upi://pay?pa=scammer-lucky@paytm&pn=GooglePay%20Cashback%20Reward&am=2500&cu=INR&tn=Claim%20Cashback"
        val analysis = QrThreatEngine.analyze(scamQr)

        assertTrue("Reverse payment QR must be flagged as fraud", analysis.isFraud)
        assertEquals("REVERSE_QR_SCAM", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
        assertTrue(analysis.title.contains("Fake 'Receive Money' QR Trap"))
    }

    @Test
    fun testQr_ImpersonationElectricityBill_flagsFraud() {
        val scamQr = "upi://pay?pa=bses-power-bill@ybl&pn=BSES%20Electricity%20Discom&am=4999&cu=INR"
        val analysis = QrThreatEngine.analyze(scamQr)

        assertTrue("Fake electricity QR must be flagged as fraud", analysis.isFraud)
        assertEquals("IMPERSONATION_SCAM", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
        assertTrue(analysis.title.contains("Impersonation"))
    }

    @Test
    fun testQr_MaliciousUrlInQr_flagsFraud() {
        val scamQr = "upi://pay?pa=customs-help@okaxis&pn=Customs%20Clearance&am=12000&url=http://bit.ly/fake-customs-apk"
        val analysis = QrThreatEngine.analyze(scamQr)

        assertTrue("Malicious URL in QR must be flagged as fraud", analysis.isFraud)
        assertEquals("MALICIOUS_LINK_QR", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
    }

    @Test
    fun testQr_AnyDeskRemoteCoercion_flagsFraud() {
        val scamQr = "upi://pay?pa=techsupport@upi&pn=Bank%20Support%20Desk&am=10&cu=INR&tn=Urgent%20AnyDesk%20Fee"
        val analysis = QrThreatEngine.analyze(scamQr)

        assertTrue("AnyDesk coercion QR must be flagged as fraud", analysis.isFraud)
        assertEquals("REMOTE_ACCESS_COERCION", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
    }

    @Test
    fun testQr_CleanLegitimateMerchant_passesAsSafe() {
        val cleanQr = "upi://pay?pa=kirana-store@icici&pn=Sharma%20Kirana%20Store&mc=5411&am=250&cu=INR"
        val analysis = QrThreatEngine.analyze(cleanQr)

        assertFalse("Clean merchant QR must NOT be flagged as fraud", analysis.isFraud)
        assertEquals("SAFE", analysis.riskLevel)
        assertTrue(analysis.riskScore < 30)
    }

    @Test
    fun testScreenText_GPayReversePaymentCashbackScam_flagsFraud() {
        // Even if scanner header "Scan any QR code" is in the view hierarchy, fraud keywords must trigger!
        val gpayScreenText = "Scan any QR code Paying GooglePay Cashback Reward scam@paytm ₹2,500.00 Claim Cashback and Receive Money to Bank"
        val analysis = QrThreatEngine.analyzeScreenText(gpayScreenText)

        assertTrue("In-app cashback scam screen text must be flagged as fraud", analysis.isFraud)
        assertEquals("REVERSE_QR_SCAM", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
        assertTrue(analysis.title.contains("Fake 'Receive Money' QR Trap"))
    }

    @Test
    fun testScreenText_GPayAnyDeskRemoteCoercion_flagsFraud() {
        val gpayScreenText = "Scan any QR code Paying Bank Support techsupport@upi ₹10.00 Urgent AnyDesk Fee"
        val analysis = QrThreatEngine.analyzeScreenText(gpayScreenText)

        assertTrue("In-app AnyDesk coercion screen text must be flagged as fraud", analysis.isFraud)
        assertEquals("REMOTE_ACCESS_COERCION", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
    }

    @Test
    fun testScreenText_GPayFakeElectricityDiscom_flagsFraud() {
        val gpayScreenText = "Scan any QR code Paying BSES Electricity Discom fake-bill@ybl ₹4,999.00 Electricity Bill Due Urgent"
        val analysis = QrThreatEngine.analyzeScreenText(gpayScreenText)

        assertTrue("In-app fake electricity discom screen text must be flagged as fraud", analysis.isFraud)
        assertEquals("IMPERSONATION_SCAM", analysis.fraudType)
        assertTrue(analysis.riskScore >= 50)
        assertTrue(analysis.title.contains("Fake Utility"))
    }

    @Test
    fun testScreenText_GPayLegitimateKiranaStore_passesClean() {
        val gpayScreenText = "Paying Ramesh Kirana Store ramesh@okicici ₹150.00 Groceries"
        val analysis = QrThreatEngine.analyzeScreenText(gpayScreenText)

        assertFalse("Clean grocery merchant screen text must NOT be flagged as fraud", analysis.isFraud)
        assertEquals("SAFE", analysis.riskLevel)
        assertTrue(analysis.riskScore < 30)
    }

    @Test
    fun testScreenText_GPayHomeScreen_passesAsClean() {
        val homeScreenText = "Scan any QR code Pay contacts Pay phone number Bank transfer Pay UPI ID Self transfer Pay bills Electricity DTH Mobile Recharge Manage your money Rewards Offers Referrals Earn Cashback with friends user@okhdfcbank"
        val analysis = QrThreatEngine.analyzeScreenText(homeScreenText)

        assertFalse("Google Pay home screen with rewards, offers & cashback banner must NEVER be flagged as fraud", analysis.isFraud)
        assertEquals("SAFE", analysis.riskLevel)
        assertEquals(0, analysis.riskScore)
        assertTrue("isHomeScreen helper must return true for GPay home screen", QrThreatEngine.isHomeScreen(homeScreenText.lowercase()))
    }

    @Test
    fun testPaymentMonitorKeywords_urgencyAndRemoteAccess_matched() {
        val noteText = "Paying Support valid@upi ₹1,500.00 Urgent AnyDesk Fee"
        val lower = noteText.lowercase()

        val matchedUrgent = PaymentMonitorService.URGENCY_TRIGGERS.filter { lower.contains(it) }
        val matchedRemote = PaymentMonitorService.REMOTE_ACCESS_TRIGGERS.filter { lower.contains(it) }

        assertTrue("Must detect 'urgent' keyword", matchedUrgent.contains("urgent"))
        assertTrue("Must detect 'anydesk' keyword", matchedRemote.contains("anydesk"))
    }

    @Test
    fun testPaymentMonitorKeywords_fraudBaitTriggers_matched() {
        val screenText = "Paying Lucky Draw winner@paytm ₹2,500.00 Enter PIN to receive Cashback Lottery"
        val lower = screenText.lowercase()

        val matchedBait = PaymentMonitorService.FRAUD_BAIT_TRIGGERS.filter { lower.contains(it) }

        assertTrue("Must detect 'enter pin to receive'", matchedBait.contains("enter pin to receive"))
        assertTrue("Must detect 'cashback lottery'", matchedBait.contains("cashback lottery"))
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

