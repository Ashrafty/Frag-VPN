package com.example.flutter_outline_vpn.domain

/**
 * Represents the various states of the VPN connection.
 */
sealed class VpnState {
    // Descriptive name for the state that gets sent to Flutter
    abstract val stateName: String
    
    object Disconnected : VpnState() {
        override val stateName: String = "disconnected"
    }
    
    object Prepare : VpnState() {
        override val stateName: String = "prepare"
    }
    
    object Authenticating : VpnState() {
        override val stateName: String = "authenticating"
    }
    
    object Connecting : VpnState() {
        override val stateName: String = "connecting"
    }
    
    object Connected : VpnState() {
        override val stateName: String = "connected"
    }
    
    object Disconnecting : VpnState() {
        override val stateName: String = "disconnecting"
    }
    
    object Denied : VpnState() {
        override val stateName: String = "denied"
    }
    
    object WaitingConnection : VpnState() {
        override val stateName: String = "waitConnection"
    }
    
    object TcpConnect : VpnState() {
        override val stateName: String = "tcpConnect"
    }
    
    object UdpConnect : VpnState() {
        override val stateName: String = "udpConnect"
    }
    
    object AssigningIp : VpnState() {
        override val stateName: String = "assignIp"
    }
    
    object Resolving : VpnState() {
        override val stateName: String = "resolve"
    }
    
    class Error(val message: String) : VpnState() {
        override val stateName: String = "error"
    }
    
    companion object {
        /**
         * Convert a state name string to a VpnState object.
         */
        fun fromString(state: String?): VpnState {
            return when(state?.lowercase()) {
                "prepare" -> Prepare
                "authenticating" -> Authenticating
                "connecting" -> Connecting
                "connected" -> Connected
                "disconnecting" -> Disconnecting
                "denied" -> Denied
                "error" -> Error("Unknown error")
                "waitconnection" -> WaitingConnection
                "tcpconnect" -> TcpConnect
                "udpconnect" -> UdpConnect
                "assignip" -> AssigningIp
                "resolve" -> Resolving
                else -> Disconnected
            }
        }
    }
} 