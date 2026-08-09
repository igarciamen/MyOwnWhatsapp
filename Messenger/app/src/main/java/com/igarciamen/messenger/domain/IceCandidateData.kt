package com.igarciamen.messenger.domain

/**
 * Representación serializable de un candidato ICE de WebRTC,
 * para poder guardarlo como documento de Firestore.
 */
data class IceCandidateData(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)