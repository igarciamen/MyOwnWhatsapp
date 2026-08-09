package com.igarciamen.messenger.domain

import org.webrtc.IceCandidate

/**
 * Conversión pura entre el modelo serializable IceCandidateData (el que
 * viaja por Firestore) y la clase nativa IceCandidate de WebRTC. Extraída
 * como funciones independientes para poder testearla sin depender del
 * resto de la infraestructura de señalización.
 */
object IceCandidateMapper {

    fun toData(candidate: IceCandidate): IceCandidateData {
        return IceCandidateData(
            sdpMid = candidate.sdpMid ?: "",
            sdpMLineIndex = candidate.sdpMLineIndex,
            candidate = candidate.sdp
        )
    }

    fun toIceCandidate(data: IceCandidateData): IceCandidate {
        return IceCandidate(data.sdpMid, data.sdpMLineIndex, data.candidate)
    }
}