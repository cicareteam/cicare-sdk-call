package cc.cicare.sdkcall.signaling

data class SignalingData (
    val token: String,
    val server: String,
    val isFromPhone: Boolean? = false
)