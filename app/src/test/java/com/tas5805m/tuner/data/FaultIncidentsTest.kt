package com.tas5805m.tuner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultIncidentsTest {
    private val t0 = 1_000_000L

    @Test fun faultBaruMembukaKejadianDanPembaruanBerikutnyaBukanStruktural() {
        val r1 = FaultIncidents.update(emptyList(), "s0", setOf("oc_l"), t0)
        assertTrue(r1.structural)
        assertEquals(1, r1.list.size)
        assertTrue(r1.list[0].active)
        val r2 = FaultIncidents.update(r1.list, "s0", setOf("oc_l"), t0 + 2000)
        assertFalse(r2.structural)                       // hanya lastSeen yang berubah → tidak perlu disimpan
        assertEquals(t0 + 2000, r2.list[0].lastSeenMs)
        assertEquals(2000L, r2.list[0].durationMs(t0 + 2000))
    }

    @Test fun faultBerakhirBilaHilang() {
        var l = FaultIncidents.update(emptyList(), "s0", setOf("uv"), t0).list
        l = FaultIncidents.update(l, "s0", setOf("uv"), t0 + 4000).list
        val r = FaultIncidents.update(l, "s0", emptySet(), t0 + 6000)
        assertTrue(r.structural)
        assertFalse(r.list[0].active)
        assertEquals(t0 + 4000, r.list[0].endMs)         // berakhir di pembacaan terakhir yang melaporkan
        assertEquals(4000L, r.list[0].durationMs(t0 + 99_000))
    }

    @Test fun kambuhDalamJendelaDigabung() {
        var l = FaultIncidents.update(emptyList(), "s1", setOf("clk"), t0).list
        l = FaultIncidents.update(l, "s1", emptySet(), t0 + 2000).list        // berakhir (endMs = t0)
        l = FaultIncidents.update(l, "s1", setOf("clk"), t0 + 5000).list      // 5 dtk setelah berakhir ≤ 6 dtk → gabung
        assertEquals(1, l.size)
        assertEquals(2, l[0].count)
        assertTrue(l[0].active)
        assertEquals(t0, l[0].startMs)
    }

    @Test fun kambuhSetelahJendelaMenjadiKejadianBaru() {
        var l = FaultIncidents.update(emptyList(), "s1", setOf("clk"), t0).list
        l = FaultIncidents.update(l, "s1", emptySet(), t0 + 2000).list
        l = FaultIncidents.update(l, "s1", setOf("clk"), t0 + 20_000).list
        assertEquals(2, l.size)
        assertEquals(1, l.count { it.active })
    }

    @Test fun sumberBerbedaTidakSalingMengganggu() {
        var l = FaultIncidents.update(emptyList(), "sat", setOf("oc_l"), t0).list
        l = FaultIncidents.update(l, "s0", setOf("oc_l"), t0 + 1000).list
        l = FaultIncidents.update(l, "s0", emptySet(), t0 + 3000).list
        assertEquals(2, l.size)
        assertTrue(l.first { it.source == "sat" }.active)      // satelit tidak ikut berakhir
        assertFalse(l.first { it.source == "s0" }.active)
    }

    @Test fun dataTerputusMenutupDenganTanda() {
        val l = FaultIncidents.update(emptyList(), "s0", setOf("ot_sd"), t0).list
        val r = FaultIncidents.closeTruncated(l, "s0")
        assertTrue(r.structural)
        assertTrue(r.list[0].truncated)
        assertFalse(r.list[0].active)
        // kejadian yang terputus tidak digabung dengan kambuhan berikutnya
        val again = FaultIncidents.update(r.list, "s0", setOf("ot_sd"), t0 + 1000).list
        assertEquals(2, again.size)
        assertNull(FaultIncidents.closeTruncated(emptyList()).list.firstOrNull())
    }

    @Test fun batasJumlahMembuangYangSudahBerakhirDanTidakMenyentuhYangAktif() {
        var l: List<FaultIncident> = emptyList()
        for (i in 0 until FaultIncidents.MAX + 20) {
            val now = t0 + i * 100_000L
            l = FaultIncidents.update(l, "s0", setOf("bq"), now).list
            l = FaultIncidents.update(l, "s0", emptySet(), now + 2000).list
        }
        l = FaultIncidents.update(l, "s0", setOf("oc_r"), t0 + 99_000_000L).list   // satu yang aktif
        assertTrue(l.size <= FaultIncidents.MAX)
        assertTrue(l.any { it.active && it.key == "oc_r" })
    }

    @Test fun formatDurasiDanWaktuLalu() {
        assertEquals("sesaat", FaultIncidents.formatDuration(400))
        assertEquals("8 dtk", FaultIncidents.formatDuration(8_000))
        assertEquals("2 mnt 5 dtk", FaultIncidents.formatDuration(125_000))
        assertEquals("3 mnt", FaultIncidents.formatDuration(180_000))
        assertEquals("1 j 3 mnt", FaultIncidents.formatDuration(3_780_000))
        assertEquals("baru saja", FaultIncidents.formatAgo(2_000))
        assertEquals("3 mnt lalu", FaultIncidents.formatAgo(190_000))
    }

    @Test fun labelSumber() {
        assertEquals("Satelit", FaultIncidents.sourceLabel("sat"))
        assertEquals("Sub wireless slot 2", FaultIncidents.sourceLabel("s2"))
        assertTrue(FaultIncidents.keyLabel("oc_l").isNotBlank())
        assertEquals("tidak_ada", FaultIncidents.keyLabel("tidak_ada"))
    }
}
