package cc.cicare.sdkcall.signaling

import cc.cicare.sdkcall.rtc.WebRTCManager
import org.webrtc.SessionDescription

class SignalingHelper(
    private val webrtcManager: WebRTCManager
) {

    fun initRTC() {
        webrtcManager.init()
        webrtcManager.initMic()
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        webrtcManager.setRemoteDescription(sdp)
    }

    fun close() {
        webrtcManager.close()
    }
}