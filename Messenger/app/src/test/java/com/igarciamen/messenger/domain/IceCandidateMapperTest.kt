package com.igarciamen.messenger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.webrtc.IceCandidate

class IceCandidateMapperTest {

    @Test
    fun `toData copia correctamente los campos de IceCandidate`() {
        val candidate = IceCandidate("audio", 0, "candidate:1 1 UDP 12345 192.168.1.1 5000 typ host")

        val data = IceCandidateMapper.toData(candidate)

        assertEquals("audio", data.sdpMid)
        assertEquals(0, data.sdpMLineIndex)
        assertEquals("candidate:1 1 UDP 12345 192.168.1.1 5000 typ host", data.candidate)
    }

    @Test
    fun `toData usa cadena vacia si sdpMid es null`() {
        val candidate = IceCandidate(null, 1, "candidate:2 1 UDP 12345 192.168.1.2 5001 typ host")

        val data = IceCandidateMapper.toData(candidate)

        assertEquals("", data.sdpMid)
    }

    @Test
    fun `toIceCandidate reconstruye un IceCandidate equivalente`() {
        val data = IceCandidateData(
            sdpMid = "video",
            sdpMLineIndex = 1,
            candidate = "candidate:3 1 UDP 12345 192.168.1.3 5002 typ host"
        )

        val candidate = IceCandidateMapper.toIceCandidate(data)

        assertEquals("video", candidate.sdpMid)
        assertEquals(1, candidate.sdpMLineIndex)
        assertEquals("candidate:3 1 UDP 12345 192.168.1.3 5002 typ host", candidate.sdp)
    }

    @Test
    fun `round-trip toData luego toIceCandidate preserva los datos`() {
        val original = IceCandidate("audio", 0, "candidate:4 1 UDP 12345 192.168.1.4 5003 typ srflx")

        val roundTripped = IceCandidateMapper.toIceCandidate(IceCandidateMapper.toData(original))

        assertEquals(original.sdpMid, roundTripped.sdpMid)
        assertEquals(original.sdpMLineIndex, roundTripped.sdpMLineIndex)
        assertEquals(original.sdp, roundTripped.sdp)
    }
}