package com.example.flutter_outline_vpn.util

import com.example.flutter_outline_vpn.util.Logger
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.Proxy as JavaProxy
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Utility for validating proxy connections and Outline keys.
 */
object ProxyValidator {
    private const val SOCKET_TIMEOUT_SECONDS = 10L
    private const val HTTP_TEST_TIMEOUT_MS = 15000
    private const val TEST_URL = "https://www.google.com"
    
    private val executorService = Executors.newCachedThreadPool()
    
    /**
     * Validate an Outline key format.
     * @param key The Outline key to validate.
     * @return true if the format is valid, false otherwise.
     */
    fun isValidOutlineKeyFormat(key: String): Boolean {
        // The key should start with ss://
        if (!key.startsWith("ss://")) {
            return false
        }
        
        return true
    }
    
    /**
     * Validate a socket connection to the proxy.
     * @param host The proxy host.
     * @param port The proxy port.
     * @return A Pair where the first element is a Boolean indicating success, and the second element is an error message if applicable.
     */
    fun validateSocketConnection(host: String, port: Int): Future<Pair<Boolean, String?>> {
        return executorService.submit(Callable {
            Logger.d("Validating socket connection to $host:$port")
            try {
                if (port <= 0) {
                    Logger.e("Invalid proxy port: $port")
                    return@Callable Pair(false, "Invalid proxy port: $port")
                }
                
                val socket = Socket()
                try {
                    socket.connect(
                        InetSocketAddress(host, port),
                        TimeUnit.SECONDS.toMillis(SOCKET_TIMEOUT_SECONDS).toInt()
                    )
                    
                    if (socket.isConnected) {
                        Logger.s("Successfully connected to proxy at $host:$port")
                        return@Callable Pair(true, null)
                    } else {
                        Logger.e("Could not establish connection to proxy")
                        return@Callable Pair(false, "Could not establish connection to proxy")
                    }
                } catch (e: IOException) {
                    Logger.e("Failed to connect to proxy", e)
                    return@Callable Pair(false, "Failed to connect to proxy: ${e.message}")
                } finally {
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        Logger.e("Error closing socket", e)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Proxy socket validation error", e)
                return@Callable Pair(false, "Proxy validation error: ${e.message}")
            }
        })
    }
    
    /**
     * Validate the proxy functionality by making an HTTP request through it.
     * @param host The proxy host.
     * @param port The proxy port.
     * @return A Future containing a Pair where the first element is a Boolean indicating success, and the second element is an error message if applicable.
     */
    fun validateHttpFunctionality(host: String, port: Int): Future<Pair<Boolean, String?>> {
        return executorService.submit(Callable {
            Logger.d("Validating HTTP functionality through $host:$port")
            try {
                val javaProxy = JavaProxy(JavaProxy.Type.HTTP, InetSocketAddress(host, port))
                
                val url = URL(TEST_URL)
                val connection = url.openConnection(javaProxy) as HttpURLConnection
                
                connection.connectTimeout = HTTP_TEST_TIMEOUT_MS
                connection.readTimeout = HTTP_TEST_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "OutlineVPN-Validator/1.0")
                
                try {
                    val responseCode = connection.responseCode
                    
                    if (responseCode in 200..399) {
                        Logger.s("Proxy functionality test succeeded with response code $responseCode")
                        return@Callable Pair(true, null)
                    } else {
                        Logger.e("Proxy functionality test failed with response code $responseCode")
                        return@Callable Pair(false, "Proxy test failed with HTTP status code $responseCode")
                    }
                } catch (e: IOException) {
                    Logger.e("Failed to connect through proxy", e)
                    return@Callable Pair(false, "Failed to connect through proxy: ${e.message}")
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Logger.e("Proxy functionality validation error", e)
                return@Callable Pair(false, "Proxy functionality validation error: ${e.message}")
            }
        })
    }
    
    /**
     * Shutdown the executor service.
     */
    fun shutdown() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
} 