package com.tas5805m.tuner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyAdvisorTest {
    private val now = 10_000_000L
    private val timing = WsubTimingInfo(latBase = 70, latCmd = 85, extraMax = 100, aligned = true)

    private fun trend(n: Int, und: (Int) -> Int = { 0 }) =
        List(n) { i -> WsubTrendPt(now - (n - 1 - i) * 2000L, 20, 86, und(i), 0, null) }

    private fun peer(rssi: Int = 0) = WsubPeer(mac = "AA", slot = 0, link = true, rssi = rssi, diag = WsubDiag(fw = 16, pkt = 32, needMs = 12f))

    @Test fun tanpaDataWaktuBelumCukup() {
        assertEquals(TuningKind.INSUFFICIENT, LatencyAdvisor.advise(peer(), trend(30), null, now).kind)
        assertEquals(TuningKind.INSUFFICIENT, LatencyAdvisor.advise(peer(), trend(5), timing, now).kind)
    }

    @Test fun offlineTidakDisarankan() {
        assertEquals(TuningKind.INSUFFICIENT, LatencyAdvisor.advise(peer().copy(link = false), trend(30), timing, now).kind)
    }

    @Test fun stabilCukup() {
        val a = LatencyAdvisor.advise(peer(rssi = -60), trend(30), timing, now)
        assertEquals(TuningKind.OK, a.kind)
        assertTrue(a.details.any { it.contains("headroom 85 ms") })
    }

    @Test fun underrunSaatAdaptasiMasihBerjalan() {
        val a = LatencyAdvisor.advise(peer(), trend(30, und = { if (it >= 20) 2 else 0 }), timing, now)
        assertEquals(TuningKind.ADAPTING, a.kind)
    }

    @Test fun underrunSaatBatasAdaptasiPenuhTidakMenyarankanTrim() {
        val full = timing.copy(latCmd = 170)   // basis 70 + maks 100
        val a = LatencyAdvisor.advise(peer(rssi = -55), trend(30, und = { if (it >= 20) 2 else 0 }), full, now)
        assertEquals(TuningKind.SATURATED, a.kind)
        assertTrue(a.details.any { it.contains("WSUB_ALIGN_MAX_EXTRA_MS") })
        assertTrue(a.details.any { it.contains("Do not use trim") })
    }

    @Test fun sinyalLemahPrioritasRadio() {
        val full = timing.copy(latCmd = 170)
        val a = LatencyAdvisor.advise(peer(rssi = -92), trend(30, und = { if (it >= 20) 2 else 0 }), full, now)
        assertEquals(TuningKind.RADIO, a.kind)
    }
}
