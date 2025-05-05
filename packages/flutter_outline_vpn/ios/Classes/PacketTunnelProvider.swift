import NetworkExtension
import os.log
import mobileproxy

// Make sure to add this extension to your app configuration and enable the Network Extension capability
class PacketTunnelProvider: NEPacketTunnelProvider {
    
    private var localProxy: Proxy?
    private var streamDialer: StreamDialer?
    private var outlineKey: String?
    
    // Logging
    private let log = OSLog(subsystem: "com.example.flutter_outline_vpn", category: "PacketTunnelProvider")
    
    // State tracking
    private var isStopping = false
    
    // Stats
    private var bytesIn: Int64 = 0
    private var bytesOut: Int64 = 0
    
    override func startTunnel(options: [String : NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        os_log("Starting VPN tunnel", log: log, type: .info)
        
        // Extract configuration from options
        guard let tunnelOptions = options as? [String: AnyObject],
              let proxyAddressOption = tunnelOptions["proxyAddress"] as? String else {
            let error = NSError(domain: "com.example.flutter_outline_vpn", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing proxy address in options"])
            completionHandler(error)
            return
        }
        
        // If we have an Outline key, use it
        outlineKey = tunnelOptions["outlineKey"] as? String
        
        do {
            // Set up local proxy if needed
            if let outlineKey = outlineKey {
                try setupOutlineProxy(outlineKey: outlineKey)
            }
            
            // Parse the proxy address
            let proxyAddress = outlineKey != nil ? (localProxy?.address() ?? proxyAddressOption) : proxyAddressOption
            let proxyParts = proxyAddress.components(separatedBy: ":")
            guard proxyParts.count == 2, 
                  let proxyHost = proxyParts.first,
                  let proxyPortString = proxyParts.last,
                  let proxyPort = Int(proxyPortString) else {
                throw NSError(domain: "com.example.flutter_outline_vpn", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid proxy address format"])
            }
            
            // Configure the VPN network settings
            let networkSettings = createNetworkSettings(proxyHost: proxyHost, proxyPort: proxyPort)
            
            // Apply the network settings and set up the packet flow
            setTunnelNetworkSettings(networkSettings) { [weak self] error in
                guard let self = self else { return }
                
                if let error = error {
                    os_log("Failed to apply network settings: %{public}@", log: self.log, type: .error, error.localizedDescription)
                    completionHandler(error)
                    return
                }
                
                // Start handling packets from the TUN interface
                self.setupPacketFlow(proxyHost: proxyHost, proxyPort: proxyPort)
                
                os_log("VPN tunnel started successfully", log: self.log, type: .info)
                completionHandler(nil)
            }
        } catch {
            os_log("Error starting VPN tunnel: %{public}@", log: log, type: .error, error.localizedDescription)
            completionHandler(error)
        }
    }
    
    private func setupOutlineProxy(outlineKey: String) throws {
        os_log("Setting up Outline proxy with key", log: log, type: .debug)
        
        do {
            // Create a stream dialer with the Outline key
            guard let dialer = try? StreamDialer(fromConfig: outlineKey) else {
                throw NSError(domain: "com.example.flutter_outline_vpn", code: 3, userInfo: [NSLocalizedDescriptionKey: "Failed to create stream dialer"])
            }
            streamDialer = dialer
            
            // Run a local proxy that uses the dialer
            guard let proxy = try? Mobileproxy.runProxy("127.0.0.1:0", dialer: dialer) else {
                throw NSError(domain: "com.example.flutter_outline_vpn", code: 4, userInfo: [NSLocalizedDescriptionKey: "Failed to start local proxy server"])
            }
            localProxy = proxy
            
            os_log("Local proxy started at %{public}@", log: log, type: .debug, proxy.address())
        } catch {
            os_log("Error setting up Outline proxy: %{public}@", log: log, type: .error, error.localizedDescription)
            throw error
        }
    }
    
    private func createNetworkSettings(proxyHost: String, proxyPort: Int) -> NEPacketTunnelNetworkSettings {
        // Create VPN interface settings
        let tunnelNetworkSettings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "10.11.12.1")
        
        // Configure IPv4 settings
        let ipv4Settings = NEIPv4Settings(addresses: ["10.11.12.13"], subnetMasks: ["255.255.255.252"])
        ipv4Settings.includedRoutes = [NEIPv4Route.default()]
        tunnelNetworkSettings.ipv4Settings = ipv4Settings
        
        // Configure DNS settings
        let dnsSettings = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8"])
        dnsSettings.matchDomains = [""] // Route all DNS queries through the VPN
        tunnelNetworkSettings.dnsSettings = dnsSettings
        
        // Set MTU
        tunnelNetworkSettings.mtu = 1500
        
        return tunnelNetworkSettings
    }
    
    private func setupPacketFlow(proxyHost: String, proxyPort: Int) {
        // Read packets from the TUN interface and process them
        packetFlow.readPackets { [weak self] packets, protocols in
            guard let self = self, !self.isStopping else { return }
            
            // Here you would use the tun2socks library to process these packets and route them through your proxy
            // This implementation depends on how you've built your tun2socks library
            
            // Example pseudocode:
            // 1. Feed packets to tun2socks
            for (index, packet) in packets.enumerated() {
                // Process each packet
                self.bytesOut += Int64(packet.count)
                
                // Here you would pass the packet to tun2socks
                // tun2socks.writePacket(packet, protocols[index])
            }
            
            // 2. Read processed packets from tun2socks and write them back to packetFlow
            // while let (packet, protocol) = tun2socks.readPacket() {
            //     self.packetFlow.writePackets([packet], withProtocols: [protocol])
            //     self.bytesIn += Int64(packet.count)
            // }
            
            // Continue reading packets
            if !self.isStopping {
                self.setupPacketFlow(proxyHost: proxyHost, proxyPort: proxyPort)
            }
        }
    }
    
    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        os_log("Stopping VPN tunnel, reason: %d", log: log, type: .info, reason.rawValue)
        
        // Mark as stopping to prevent further packet processing
        isStopping = true
        
        // Clean up resources
        if let localProxy = localProxy {
            localProxy.stop(5) // 5 second timeout
        }
        
        self.localProxy = nil
        self.streamDialer = nil
        
        // Call completion handler
        completionHandler()
    }
    
    override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)?) {
        // Handle messages from the container app (e.g. for stats)
        guard let message = try? JSONSerialization.jsonObject(with: messageData, options: []) as? [String: Any],
              let type = message["type"] as? String else {
            completionHandler?(nil)
            return
        }
        
        if type == "getStats" {
            // Return current statistics
            let stats: [String: Any] = [
                "bytesIn": bytesIn,
                "bytesOut": bytesOut
            ]
            
            if let statsData = try? JSONSerialization.data(withJSONObject: stats, options: []) {
                completionHandler?(statsData)
            } else {
                completionHandler?(nil)
            }
        } else {
            completionHandler?(nil)
        }
    }
}