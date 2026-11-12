package com.tas5805m.tuner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WsubHealthTest {
    private val now = 10_000_000L

    private fun trend(
        n: Int, step: Long = 2000, rx: Int = 86, fill: Int = 20,
        und: (Int) -> Int = { 0 }, gap: (Int) -> Int = { 0 }, lvl: (Int) -> Int? = { null }, up: (Int) -> Int = { -1 },
    ) = List(n) { i -> WsubTrendPt(now - (n - 1 - i) * step, fill, rx, und(i), gap(i), lvl(i), up(i)) }

    private fun peer(
        link: Boolean = true, hiz: Boolean = false, rate: Int = 44100, rx: Int = 86, flt: Int = 0, fcnt: Int = 0,
        diag: WsubDiag? = WsubDiag(fw = 16, pkt = 32, fillMinMs = 10f, gmaxMs = 20),
        upS: Int = -1, rst: Int = -1, rssi: Int = 0,
    ) = WsubPeer(mac = "AA:BB:CC:DD:EE:FF", slot = 0, link = link, fillMs = 20, rate = rate, hiz = hiz, rxAudio = rx, flt = flt, fcnt = fcnt, diag = diag, upS = upS, rst = rst, rssi = rssi)

    private val freshSeen get() = WsubSeen(statMs = now - 1000, diagMs = now - 1000, lvlMs = now - 500)

    private fun eval(p: WsubPeer, t: List<WsubTrendPt>, seen: WsubSeen? = freshSeen, lvl: Int? = null) =
        WsubHealthEval.evaluate(p, t, lvl, seen, now, 16)

    @Test fun offline() {
        assertEquals(HealthLevel.OFF, eval(peer(link = false), trend(10)).level)
    }

    @Test fun dataSemuaValidBaruBolehSehat() {
        val h = eval(peer(), trend(10))
        assertEquals(HealthLevel.OK, h.level)
        assertTrue(h.coverage.filter { it.required }.all { it.ok })
    }

    @Test fun tanpaStatusBukanSehat() {
        val h = eval(peer(), trend(10), seen = null)
        assertEquals(HealthLevel.UNKNOWN, h.level)         // nilai tampak bagus, tapi tidak ada bukti segar
    }

    @Test fun statusBasiBukanSehat() {
        val h = eval(peer(), trend(10), seen = WsubSeen(statMs = now - 30_000))
        assertEquals(HealthLevel.UNKNOWN, h.level)
        assertTrue(h.reasons.any { it.text.contains("basi") })
    }

    @Test fun trenBelumCukupBukanSehat() {
        assertEquals(HealthLevel.UNKNOWN, eval(peer(), trend(3)).level)
    }

    @Test fun slaveLamaTanpaFaultBukanSehat() {
        assertEquals(HealthLevel.UNKNOWN, eval(peer(flt = -1), trend(10)).level)
    }

    @Test fun faultKritisTetapBurukWalauDataBasi() {
        val oc = 1 shl FAULT_DEFS.indexOfFirst { it.key == "oc_l" }
        val h = eval(peer(flt = oc), trend(10), seen = WsubSeen(statMs = now - 30_000))
        assertEquals(HealthLevel.BAD, h.level)             // temuan tidak boleh disembunyikan oleh data basi
        assertTrue(h.reasons.any { it.text.contains("data lama") })
    }

    @Test fun ambangRadioMengikutiLajuSample() {
        // 48 kHz, paket 32 sample → seharusnya ≈ 93,75/dtk; 86/dtk masih wajar
        val ok = eval(peer(rate = 48000, rx = 86), trend(10, rx = 86))
        assertEquals(HealthLevel.OK, ok.level)
        // 44,1 kHz → seharusnya ≈ 86/dtk; 60/dtk jelas turun
        val bad = eval(peer(rate = 44100, rx = 60), trend(10, rx = 60))
        assertEquals(HealthLevel.WARN, bad.level)
        assertEquals(HealthLevel.BAD, eval(peer(rate = 44100, rx = 30), trend(10, rx = 30)).level)
    }

    @Test fun expectedRxDariRateDanUkuranPaket() {
        assertEquals(44100.0 / 512, WsubHealthEval.expectedRx(peer())!!, 1e-9)
        assertEquals(null, WsubHealthEval.expectedRx(peer(diag = null)))
    }

    @Test fun underrunDinormalkanPerMenit() {
        // 3 underrun dalam 18 dtk → ≈ 10/menit → BAD; 1 underrun → WARN
        val bad = eval(peer(), trend(10, und = { if (it >= 7) 3 else if (it >= 4) 1 else 0 }))
        assertEquals(HealthLevel.BAD, bad.level)
        val warn = eval(peer(), trend(30, und = { if (it >= 15) 1 else 0 }))
        assertEquals(HealthLevel.WARN, warn.level)
    }

    @Test fun counterResetTidakDihitungNegatif() {
        assertEquals(2, WsubHealthEval.rise(listOf(5, 6, 7, 0, 0)))
    }

    @Test fun standbyTidakDinilaiUnderrun() {
        val t = trend(10, rx = 0, und = { it * 3 })
        val h = eval(peer(hiz = true, rx = 0), t)
        assertFalse(h.level == HealthLevel.WARN || h.level == HealthLevel.BAD)
    }

    @Test fun levelBerkelanjutanBukanSatuSampel() {
        assertEquals(HealthLevel.OK, eval(peer(), trend(10, lvl = { if (it == 4) -20 else -300 })).level)
        assertEquals(HealthLevel.WARN, eval(peer(), trend(10, lvl = { if (it % 3 == 0) -20 else -300 })).level)
        assertEquals(HealthLevel.WARN, eval(peer(), trend(10, lvl = { if (it == 4) -2 else -300 })).level)
    }

    @Test fun jedaAudioMelebihiBuffer() {
        val h = eval(peer(diag = WsubDiag(fw = 16, pkt = 32, gmaxMs = 120)), trend(10))
        assertEquals(HealthLevel.WARN, h.level)
    }

    @Test fun brownoutBaruSajaBuruk() {
        val h = eval(peer(upS = 45, rst = 9), trend(10))
        assertEquals(HealthLevel.BAD, h.level)
        assertTrue(h.reasons.any { it.advice?.contains("power supply") == true })
    }

    @Test fun brownoutLamaHanyaInfo() {
        val h = eval(peer(upS = 7200, rst = 9), trend(10))
        assertEquals(HealthLevel.OK, h.level)
    }

    @Test fun powerOnNormalTidakDinilaiBuruk() {
        assertEquals(HealthLevel.OK, eval(peer(upS = 20, rst = 1), trend(10)).level)
    }

    @Test fun rebootTerdeteksiDariUptimeTurun() {
        val h = eval(peer(upS = 30, rst = 3), trend(10, up = { if (it < 5) 1000 + it * 2 else (it - 5) * 2 }))
        assertEquals(HealthLevel.WARN, h.level)
        assertTrue(h.reasons.any { it.text.contains("reboot") })
    }

    @Test fun rssiLemahWaspada() {
        val h = eval(peer(rssi = -90), trend(10))
        assertEquals(HealthLevel.WARN, h.level)
        assertTrue(h.reasons.any { it.advice?.contains("closer") == true })
    }

    @Test fun underrunDenganSinyalKuatMenyarankanInterferensi() {
        val h = eval(peer(rssi = -55), trend(10, und = { if (it >= 7) 3 else 0 }))
        assertEquals(HealthLevel.BAD, h.level)
        assertTrue(h.reasons.any { it.advice?.contains("interference") == true })
    }

    @Test fun beda_firmware_masterDanSlave() {
        val h = eval(peer(diag = WsubDiag(fw = 13, pkt = 32, gmaxMs = 20)), trend(10))
        assertEquals(HealthLevel.WARN, h.level)
    }
}
