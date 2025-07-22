package cc.cicare.sdkcall.signaling

import android.util.Log
import cc.cicare.sdkcall.event.CallEventListener
import cc.cicare.sdkcall.event.CallState
import cc.cicare.sdkcall.rtc.WebRTCManager
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.SessionDescription

/**
 * Manages the socket connection for signaling between peers in a WebRTC call.
 *
 * This class establishes and listens to signaling events through a WebSocket
 * connection using the Socket.IO client. It communicates SDP offers/answers and
 * call control events such as HANGUP, RINGING, etc.
 *
 * @property callEventListener A listener to receive call state updates.
 * @property webrtcManager Reference to WebRTC manager to handle SDP and media.
 */
class SocketManager(
    private val callEventListener: CallEventListener?,
    private val webrtcManager: WebRTCManager
) {
    private var socket: Socket? = null

    /**
     * Connects to the signaling server via WebSocket using Socket.IO protocol.
     *
     * @param wssUrl The WebSocket URL (e.g. wss://example.com).
     * @param token Authentication token passed as query parameter.
     */
    fun connect(wssUrl: String, token: String) {
        val opts = IO.Options().apply {
            query = "token=$token"
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
        }

        socket = IO.socket(wssUrl, opts)
        socket?.connect()

        // Event when the callee accepts the call
        socket?.on("ACCEPTED") { _ ->
            callEventListener?.onCallStateChanged(CallState.CONNECTED)
        }

        // Event when the callee accepts the call
        socket?.on("CONNECTED") { _ ->
            callEventListener?.onCallStateChanged(CallState.CONNECTED)
        }

        // Event when the call is ended from either side
        socket?.on("HANGUP") { _ ->
            callEventListener?.onCallStateChanged(CallState.ENDED)
            webrtcManager.close()
            socket?.disconnect()
        }

        // Received SDP offer from the remote peer
        socket?.on("SDP_OFFER") { args ->
            //callEventListener.onCallStateChanged(CallState.CONNECTING)
            val json = args[0] as JSONObject
            val sdpString = json.getString("sdp")
            webrtcManager.init()
            webrtcManager.initMic()

            val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
            webrtcManager.setRemoteDescription(sdp)
        }

        // Ringing event sent to callee to indicate incoming call
        socket?.on("RINGING") { _ ->
            callEventListener?.onCallStateChanged(CallState.RINGING)
        }

        // Received SDP answer from remote peer
        socket?.on("SDP_ANSWER") { args ->
            //callEventListener.onCallStateChanged(CallState.CONNECTING)
            val json = args[0] as JSONObject
            val sdpString = json.getString("sdp")
            Log.i("SDP_OFFER", sdpString)
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
            webrtcManager.setRemoteDescription(sdp)
        }
    }

    /**
     * Sends a signaling event through the WebSocket connection.
     *
     * @param event The event name (e.g. SDP_OFFER, SDP_ANSWER, etc.)
     * @param data The event payload in JSON format.
     */
    fun send(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    /**
     * Disconnects the WebSocket connection.
     */
    fun disconnect() {
        socket?.disconnect()
    }
}