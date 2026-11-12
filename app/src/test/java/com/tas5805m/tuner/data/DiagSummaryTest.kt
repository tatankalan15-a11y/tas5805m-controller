package com.tas5805m.tuner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagSummaryTest {
    private val now = 10_000_000L

    private fun state(seen: WsubSeen?, flt: Int = 0, und: Int = 0): TunerState {
        val peer = WsubPeer(
            mac = "AA:BB:CC:DD:EE:FF", slot = 0, link = true, fillMs = 105, rate = 44100, rxAudio = 86, flt = flt, und = und,
            diag = WsubDiag(fw = 16, pkt = 32, gmaxMs = 20, fillMinMs = 50f),
        )
        val trend = List(10) { i -> WsubTrendPt(now - (9 - i) * 2000L, 105, 86, und, 0, -125) }
        return TunerState(
            wsub = WsubState(peers = listOf(peer), mfw = 16, ackOk = 998, ackFail = 2),
            wsubLvl = mapOf(0 to -125),
            wsubTrend = mapOf(0 to trend),
            wsubSeen = if (seen == null) emptyMap() else mapOf(0 to seen),
        )
    }

    @Test fun sehatSesuaiFormat() {
        val t = DiagSummary.build(state(WsubSeen(now - 1000, now - 1000, now - 500)), now)
        assertTrue(t.startsWith("WIRELESS SUB DIAGNOSTIC"))
        assertTrue(t.contains("Overall       : HEALTHY"))
        assertTrue(t.contains("Master FW     : 2.9.16"))
        assertTrue(t.contains("Slave FW      : 2.9.16"))
        assertTrue(t.contains("Level         : -12.5 dB"))
        assertTrue(t.contains("Buffer        : 105 ms"))
        assertTrue(t.contains("ACK Success   : 99.8%"))
        assertTrue(t.contains("ACK Failure   : 0.2%"))
        assertTrue(t.contains("Fault         : NONE"))
        assertTrue(t.contains("No abnormal condition detected."))
        assertFalse(t.contains("FINDINGS"))
    }

    @Test fun tanpaDataSegarTidakBolehHealthy() {
        val t = DiagSummary.build(state(null), now)
        assertTrue(t.contains("Overall       : UNVERIFIED"))
        assertFalse(t.contains("No abnormal condition detected."))
        assertTrue(t.contains("FINDINGS"))
    }

    @Test fun faultMenghasilkanCriticalDanSaran() {
        val oc = 1 shl FAULT_DEFS.indexOfFirst { it.key == "oc_l" }
        val t = DiagSummary.build(state(WsubSeen(now - 1000, now - 1000, now - 500), flt = oc), now)
        assertTrue(t.contains("Overall       : CRITICAL"))
        assertTrue(t.contains("Check speaker wiring"))
    }
}
