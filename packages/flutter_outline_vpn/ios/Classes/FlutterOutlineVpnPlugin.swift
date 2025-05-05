import Flutter
import UIKit
import NetworkExtension
import mobileproxy

public class FlutterOutlineVpnPlugin: NSObject, FlutterPlugin {
  private var methodChannel: FlutterMethodChannel?
  private var stageEventChannel: FlutterEventChannel?
  private var statusEventChannel: FlutterEventChannel?
  
  private var stageStreamHandler = StageStreamHandler()
  private var statusStreamHandler = StatusStreamHandler()
  
  private var vpnManager: NETunnelProviderManager?
  private var providerBundleIdentifier: String?
  private var groupIdentifier: String?
  private var localizedDescription: String?
  private var observerAdded = false
  private var proxy: Proxy?
  private var connectionStartTime: Date?
  private var statsTimer: Timer?
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_outline_vpn", binaryMessenger: registrar.messenger())
    let stageChannel = FlutterEventChannel(name: "flutter_outline_vpn/stage", binaryMessenger: registrar.messenger())
    let statusChannel = FlutterEventChannel(name: "flutter_outline_vpn/status", binaryMessenger: registrar.messenger())
    
    let instance = FlutterOutlineVpnPlugin()
    instance.methodChannel = channel
    instance.stageEventChannel = stageChannel
    instance.statusEventChannel = statusChannel
    
    stageChannel.setStreamHandler(instance.stageStreamHandler)
    statusChannel.setStreamHandler(instance.statusStreamHandler)
    
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  deinit {
    removeVpnStatusObserver()
    stopStatsCollection()
  }
  
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "initialize":
      initialize(call, result: result)
    case "connect":
      connect(call, result: result)
    case "disconnect":
      disconnect(result: result)
    case "isConnected":
      isConnected(result: result)
    case "getCurrentStage":
      getCurrentStage(result: result)
    case "getStatus":
      getStatus(result: result)
    case "requestPermission":
      // On iOS, permission is handled during connection
      result(true)
    case "dispose":
      dispose(result: result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  // MARK: - Method Implementations
  
  private func initialize(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard let args = call.arguments as? [String: Any] else {
      result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments", details: nil))
      return
    }
    
    providerBundleIdentifier = args["providerBundleIdentifier"] as? String
    localizedDescription = args["localizedDescription"] as? String
    groupIdentifier = args["groupIdentifier"] as? String
    
    // Get the saved tunnel provider manager
    loadVpnManager { [weak self] success in
      guard let self = self else { return }
      
      if success {
        self.addVpnStatusObserver()
      }
      
      result(nil)
    }
  }
  
  private func connect(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard let args = call.arguments as? [String: Any],
          let outlineKey = args["outline_key"] as? String,
          let name = args["name"] as? String else {
      result(FlutterError(code: "INVALID_ARGUMENTS", message: "Outline key and name are required", details: nil))
      return
    }
    
    let port = args["port"] as? String
    let notificationConfig = args["notificationConfig"] as? [String: Any]
    
    // Check if we need to load or create the manager
    if vpnManager == nil {
      loadVpnManager { [weak self] success in
        guard let self = self, success else {
          result(FlutterError(code: "LOAD_ERROR", message: "Failed to load VPN configuration", details: nil))
          return
        }
        
        self.performConnection(outlineKey: outlineKey, port: port, name: name, notificationConfig: notificationConfig, result: result)
      }
    } else {
      performConnection(outlineKey: outlineKey, port: port, name: name, notificationConfig: notificationConfig, result: result)
    }
  }
  
  private func performConnection(outlineKey: String, port: String?, name: String, notificationConfig: [String: Any]?, result: @escaping FlutterResult) {
    // First try to create the local proxy
    do {
      let streamDialer = MobileproxyNewStreamDialerFromConfig(outlineKey, nil)
      guard let dialer = streamDialer else {
        result(FlutterError(code: "DIALER_ERROR", message: "Failed to create stream dialer", details: nil))
        return
      }
      
      // Determine proxy address
      let proxyAddress = "127.0.0.1:\(port ?? "0")"
      
      // Run the proxy
      let localProxy = MobileproxyRunProxy(proxyAddress, dialer, nil)
      guard let proxy = localProxy else {
        result(FlutterError(code: "PROXY_ERROR", message: "Failed to start proxy", details: nil))
        return
      }
      
      self.proxy = proxy
      
      // Store the connection time and start stats collection
      connectionStartTime = Date()
      startStatsCollection()
      
      // Create or update VPN configuration
      createOrUpdateVpnConfiguration(proxyAddress: proxy.address(), name: name) { [weak self] success in
        guard let self = self else { return }
        
        if success {
          // Start the VPN tunnel
          self.stageStreamHandler.sendStage("connecting")
          self.startVpnTunnel { error in
            if let error = error {
              self.stageStreamHandler.sendStage("error")
              result(FlutterError(code: "CONNECTION_ERROR", message: "Failed to start VPN: \(error.localizedDescription)", details: nil))
            } else {
              result(nil)
            }
          }
        } else {
          self.stopLocalProxy()
          result(FlutterError(code: "CONFIG_ERROR", message: "Failed to create VPN configuration", details: nil))
        }
      }
    } catch {
      result(FlutterError(code: "PROXY_ERROR", message: "Failed to set up proxy: \(error.localizedDescription)", details: nil))
    }
  }
  
  private func disconnect(result: @escaping FlutterResult) {
    stageStreamHandler.sendStage("disconnecting")
    
    stopVpnTunnel { [weak self] error in
      guard let self = self else { return }
      
      self.stopLocalProxy()
      
      if let error = error {
        result(FlutterError(code: "DISCONNECT_ERROR", message: "Failed to stop VPN: \(error.localizedDescription)", details: nil))
      } else {
        self.stageStreamHandler.sendStage("disconnected")
        result(nil)
      }
    }
  }
  
  private func isConnected(result: @escaping FlutterResult) {
    guard let manager = vpnManager else {
      result(false)
      return
    }
    
    manager.loadFromPreferences { [weak self] error in
      guard let self = self else { return }
      
      if let error = error {
        print("Failed to load VPN preferences: \(error.localizedDescription)")
        result(false)
        return
      }
      
      let status = manager.connection.status
      result(status == .connected || status == .connecting || status == .reasserting)
    }
  }
  
  private func getCurrentStage(result: @escaping FlutterResult) {
    guard let manager = vpnManager else {
      result("disconnected")
      return
    }
    
    manager.loadFromPreferences { [weak self] error in
      guard let self = self else { return }
      
      if let error = error {
        print("Failed to load VPN preferences: \(error.localizedDescription)")
        result("error")
        return
      }
      
      let status = manager.connection.status
      switch status {
      case .connected:
        result("connected")
      case .connecting:
        result("connecting")
      case .disconnecting:
        result("disconnecting")
      case .disconnected:
        result("disconnected")
      case .reasserting:
        result("connecting")
      case .invalid:
        result("error")
      @unknown default:
        result("unknown")
      }
    }
  }
  
  private func getStatus(result: @escaping FlutterResult) {
    let status = createStatusJson()
    result(status)
  }
  
  private func dispose(result: @escaping FlutterResult) {
    stopLocalProxy()
    removeVpnStatusObserver()
    stopStatsCollection()
    result(nil)
  }
  
  // MARK: - Helper Methods
  
  private func loadVpnManager(completion: @escaping (Bool) -> Void) {
    NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, error in
      guard let self = self else { return completion(false) }
      
      if let error = error {
        print("Failed to load VPN configurations: \(error.localizedDescription)")
        completion(false)
        return
      }
      
      if let manager = managers?.first {
        // Found an existing configuration
        self.vpnManager = manager
        completion(true)
      } else {
        // Create a new configuration
        let manager = NETunnelProviderManager()
        self.vpnManager = manager
        completion(true)
      }
    }
  }
  
  private func createOrUpdateVpnConfiguration(proxyAddress: String, name: String, completion: @escaping (Bool) -> Void) {
    guard let manager = vpnManager else {
      completion(false)
      return
    }
    
    // Ensure we have the provider bundle ID
    guard let providerBundleId = providerBundleIdentifier else {
      print("Provider bundle identifier is required")
      completion(false)
      return
    }
    
    // Split proxy address to host and port
    let components = proxyAddress.components(separatedBy: ":")
    guard components.count == 2,
          let portStr = components.last,
          let port = Int(portStr) else {
      print("Invalid proxy address format")
      completion(false)
      return
    }
    
    let protocolConfiguration = NETunnelProviderProtocol()
    protocolConfiguration.providerBundleIdentifier = providerBundleId
    protocolConfiguration.serverAddress = name
    
    if let appGroup = groupIdentifier {
      protocolConfiguration.providerConfiguration = [
        "proxy_host": components[0],
        "proxy_port": port,
        "app_group": appGroup
      ]
    } else {
      protocolConfiguration.providerConfiguration = [
        "proxy_host": components[0],
        "proxy_port": port
      ]
    }
    
    manager.protocolConfiguration = protocolConfiguration
    manager.localizedDescription = localizedDescription ?? name
    manager.isEnabled = true
    
    manager.saveToPreferences { error in
      if let error = error {
        print("Failed to save VPN configuration: \(error.localizedDescription)")
        completion(false)
        return
      }
      
      completion(true)
    }
  }
  
  private func startVpnTunnel(completion: @escaping (Error?) -> Void) {
    guard let manager = vpnManager else {
      completion(NSError(domain: "com.example.flutter_outline_vpn", code: -1, userInfo: [NSLocalizedDescriptionKey: "VPN manager not initialized"]))
      return
    }
    
    do {
      try manager.connection.startVPNTunnel()
      completion(nil)
    } catch {
      completion(error)
    }
  }
  
  private func stopVpnTunnel(completion: @escaping (Error?) -> Void) {
    guard let manager = vpnManager else {
      completion(nil)
      return
    }
    
    manager.connection.stopVPNTunnel()
    completion(nil)
  }
  
  private func stopLocalProxy() {
    // Stop stats collection
    stopStatsCollection()
    
    // Stop the proxy
    if let proxy = proxy {
      proxy.stop(5) // 5 second timeout
      self.proxy = nil
    }
    
    connectionStartTime = nil
  }
  
  private func addVpnStatusObserver() {
    if observerAdded { return }
    
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(vpnStatusDidChange(_:)),
      name: NSNotification.Name.NEVPNStatusDidChange,
      object: nil
    )
    
    observerAdded = true
  }
  
  private func removeVpnStatusObserver() {
    if observerAdded {
      NotificationCenter.default.removeObserver(
        self,
        name: NSNotification.Name.NEVPNStatusDidChange,
        object: nil
      )
      
      observerAdded = false
    }
  }
  
  @objc private func vpnStatusDidChange(_ notification: Notification) {
    guard let connection = notification.object as? NEVPNConnection else { return }
    
    switch connection.status {
    case .invalid:
      stageStreamHandler.sendStage("error")
    case .disconnected:
      stageStreamHandler.sendStage("disconnected")
      stopLocalProxy()
    case .connecting:
      stageStreamHandler.sendStage("connecting")
    case .connected:
      stageStreamHandler.sendStage("connected")
      if connectionStartTime == nil {
        connectionStartTime = Date()
        startStatsCollection()
      }
    case .reasserting:
      stageStreamHandler.sendStage("connecting")
    case .disconnecting:
      stageStreamHandler.sendStage("disconnecting")
    @unknown default:
      stageStreamHandler.sendStage("unknown")
    }
  }
  
  private func startStatsCollection() {
    // Stop any existing timer
    stopStatsCollection()
    
    // Start a new timer to update stats every second
    statsTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
      guard let self = self else { return }
      self.updateStats()
    }
  }
  
  private func stopStatsCollection() {
    statsTimer?.invalidate()
    statsTimer = nil
  }
  
  private func updateStats() {
    let statusJson = createStatusJson()
    statusStreamHandler.sendStatus(statusJson)
  }
  
  private func createStatusJson() -> String {
    let durationSeconds = connectionStartTime != nil ? Int(Date().timeIntervalSince(connectionStartTime!)) : 0
    let hours = durationSeconds / 3600
    let minutes = (durationSeconds % 3600) / 60
    let seconds = durationSeconds % 60
    let durationString = String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    
    // In a real implementation, you would collect actual traffic statistics
    // from the NetworkExtension APIs. This is a simplified example.
    let dict: [String: Any] = [
      "connectedOn": connectionStartTime?.timeIntervalSince1970 ?? 0,
      "duration": durationString,
      "byteIn": "0",
      "byteOut": "0",
      "packetsIn": "0",
      "packetsOut": "0"
    ]
    
    if let jsonData = try? JSONSerialization.data(withJSONObject: dict),
       let jsonString = String(data: jsonData, encoding: .utf8) {
      return jsonString
    } else {
      return "{}"
    }
  }
}

// MARK: - Stream Handlers

class StageStreamHandler: NSObject, FlutterStreamHandler {
  private var eventSink: FlutterEventSink?
  
  func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    eventSink = events
    return nil
  }
  
  func onCancel(withArguments arguments: Any?) -> FlutterError? {
    eventSink = nil
    return nil
  }
  
  func sendStage(_ stage: String) {
    DispatchQueue.main.async { [weak self] in
      self?.eventSink?(stage)
    }
  }
}

class StatusStreamHandler: NSObject, FlutterStreamHandler {
  private var eventSink: FlutterEventSink?
  
  func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    eventSink = events
    return nil
  }
  
  func onCancel(withArguments arguments: Any?) -> FlutterError? {
    eventSink = nil
    return nil
  }
  
  func sendStatus(_ status: String) {
    DispatchQueue.main.async { [weak self] in
      self?.eventSink?(status)
    }
  }
} 