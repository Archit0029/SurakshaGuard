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
    fun testAllFourScamCases_rawUriAnalysis_flaggedAsFraud() {
        val case1 = "upi://pay?pa=scammer-lucky@paytm&pn=GooglePay%20Cashback%20Reward&am=2500&cu=INR&tn=Claim%20Cashback"
        val case2 = "upi://pay?pa=bses-power-bill@ybl&pn=BSES%20Electricity%20Discom&am=4999&cu=INR&tn=Electricity%20Bill%20Due%20Urgent"
        val case3 = "upi://pay?pa=techsupport@upi&pn=Bank%20Support%20Desk&am=10&cu=INR&tn=Urgent%20AnyDesk%20Fee"
        val case4 = "upi://pay?pa=customs-help@okaxis&pn=Customs%20Clearance&am=12000&cu=INR&url=http://bit.ly/fake-customs-apk"

        val a1 = QrThreatEngine.analyze(case1)
        val a2 = QrThreatEngine.analyze(case2)
        val a3 = QrThreatEngine.analyze(case3)
        val a4 = QrThreatEngine.analyze(case4)

        assertTrue("Case 1 (Cashback trap) must be fraud", a1.isFraud)
        assertTrue("Case 2 (Fake Electricity Discom) must be fraud", a2.isFraud)
        assertTrue("Case 3 (AnyDesk Coercion) must be fraud", a3.isFraud)
        assertTrue("Case 4 (Malicious URL) must be fraud", a4.isFraud)
    }

    @Test
    fun testAllFourScamCases_screenTextAnalysis_flaggedAsFraud() {
        // Case 1: GPay checkout screen with note or payee
        val screenCase1 = "Paying Lucky scammer-lucky@paytm ₹2,500.00 Claim Cashback Proceed to pay"
        val a1 = QrThreatEngine.analyzeScreenText(screenCase1)
        assertTrue("Case 1 screen text must be flagged", a1.isFraud)
        assertEquals("REVERSE_QR_SCAM", a1.fraudType)

        // Case 2: GPay checkout screen with BSES electricity
        val screenCase2 = "Paying BSES Electricity Discom bses-power-bill@ybl ₹4,999.00 Electricity Bill Due Urgent Pay ₹4,999"
        val a2 = QrThreatEngine.analyzeScreenText(screenCase2)
        assertTrue("Case 2 screen text must be flagged", a2.isFraud)
        assertEquals("IMPERSONATION_SCAM", a2.fraudType)

        // Case 3: GPay checkout screen with AnyDesk fee
        val screenCase3 = "Paying Bank Support Desk techsupport@upi ₹10.00 Urgent AnyDesk Fee Pay ₹10"
        val a3 = QrThreatEngine.analyzeScreenText(screenCase3)
        assertTrue("Case 3 screen text must be flagged", a3.isFraud)
        assertEquals("REMOTE_ACCESS_COERCION", a3.fraudType)

        // Case 4: GPay checkout screen with customs clearance and bit.ly
        val screenCase4 = "Paying Customs Clearance customs-help@okaxis ₹12,000.00 bit.ly/fake-customs-apk Proceed to pay"
        val a4 = QrThreatEngine.analyzeScreenText(screenCase4)
        assertTrue("Case 4 screen text must be flagged", a4.isFraud)
        assertTrue(a4.isFraud)
    }

    @Test
    fun testPaymentMonitorService_hasBlatantScamMarker_detectsAllCases() {
        assertTrue(QrThreatEngine.hasBlatantScamMarker("paying googlepay cashback reward ₹2,500"))
        assertTrue(QrThreatEngine.hasBlatantScamMarker("claim cashback on this qr"))
        assertTrue(QrThreatEngine.hasBlatantScamMarker("paying bses electricity discom ₹4,999"))
        assertTrue(QrThreatEngine.hasBlatantScamMarker("electricity bill due urgent"))
        assertTrue(QrThreatEngine.hasBlatantScamMarker("urgent anydesk fee"))
        assertTrue(QrThreatEngine.hasBlatantScamMarker("customs clearance bit.ly/fake-apk"))
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

    // ── Debit OTP Fraud Interceptor Tests ──

    @Test
    fun testDebitOtpEngine_sbiDebitOtp_detectedWithAmountAndCode() {
        val sms = "Dear SBI User, 738291 is your OTP to approve debit of Rs. 14,500.00 from your SBI Card ending 4321 for online txn. Never share OTP."
        val analysis = DebitOtpEngine.analyze(sms, sender = "SBIBNK")

        assertTrue("SBI debit OTP must be identified as debit OTP", analysis.isDebitOtp)
        assertEquals("738291", analysis.otpCode)
        assertEquals("14,500.00", analysis.amount)
        assertTrue("Bank name must detect SBI", analysis.bankOrCard?.contains("SBI") == true)
        assertTrue("Threat title must warn of debit OTP", analysis.threatTitle.contains("DEBIT OTP"))
    }

    @Test
    fun testDebitOtpEngine_hdfcDebitOtp_detectedWithAmountAndCode() {
        val sms = "849201 is your secret OTP for debit of INR 49,999.00 from HDFC Bank A/c **1092. Do not disclose."
        val analysis = DebitOtpEngine.analyze(sms, sender = "HDFCBK")

        assertTrue("HDFC debit OTP must be identified as debit OTP", analysis.isDebitOtp)
        assertEquals("849201", analysis.otpCode)
        assertEquals("49,999.00", analysis.amount)
        assertTrue("Bank name must detect HDFC", analysis.bankOrCard?.contains("HDFC") == true)
    }

    @Test
    fun testDebitOtpEngine_iciciTransferOtp_detected() {
        val sms = "OTP is 192837 for fund transfer of ₹25000 from ICICI Bank account XX3829. Valid for 5 mins."
        val analysis = DebitOtpEngine.analyze(sms, sender = "ICICIB")

        assertTrue("ICICI fund transfer OTP must be identified as debit OTP", analysis.isDebitOtp)
        assertEquals("192837", analysis.otpCode)
        assertEquals("25000", analysis.amount)
        assertTrue("Bank name must detect ICICI", analysis.bankOrCard?.contains("ICICI") == true)
    }

    @Test
    fun testDebitOtpEngine_creditDepositSms_ignored() {
        val creditSms = "Your account 9912 is credited with INR 5,000.00 on 31-Oct-26. Avl Bal: INR 15,200.00 - PNB"
        val analysis = DebitOtpEngine.analyze(creditSms, sender = "PNBSMS")

        assertFalse("Credit/deposit SMS must NOT be treated as debit OTP", analysis.isDebitOtp)
        assertNull(analysis.otpCode)
        assertNull(analysis.amount)
    }

    @Test
    fun testDebitOtpEngine_nonFinancialLoginOtp_ignored() {
        val waSms = "Your WhatsApp code is 382910. Do not share with anyone."
        val waAnalysis = DebitOtpEngine.analyze(waSms, sender = "WhatsApp")
        assertFalse("WhatsApp login code must NOT trigger debit OTP alert", waAnalysis.isDebitOtp)

        val zomatoSms = "Zomato: 5821 is your verification code to log in."
        val zomatoAnalysis = DebitOtpEngine.analyze(zomatoSms, sender = "ZOMATO")
        assertFalse("Food app login code must NOT trigger debit OTP alert", zomatoAnalysis.isDebitOtp)
    }

    @Test
    fun testDebitOtpEngine_extractAmount_parsesVariousFormats() {
        assertEquals("15,000", DebitOtpEngine.extractAmount("Payment of ₹15,000 to merchant"))
        assertEquals("4,999.50", DebitOtpEngine.extractAmount("Debit of INR 4,999.50 authorized"))
        assertEquals("250", DebitOtpEngine.extractAmount("for Rs. 250 on debit card"))
    }

    @Test
    fun testDebitOtpEngine_extractOtpCode_parsesVariousPatterns() {
        assertEquals("591823", DebitOtpEngine.extractOtpCode("Your OTP is 591823 for txn"))
        assertEquals("492014", DebitOtpEngine.extractOtpCode("Verification code: 492014"))
        assertEquals("392019", DebitOtpEngine.extractOtpCode("392019 is your secret OTP to approve"))
    }

    @Test
    fun testMasterProtectionConstant_configuredProperly() {
        assertEquals("master_protection_enabled", PaymentMonitorService.KEY_MASTER_PROTECTION)
    }
}

