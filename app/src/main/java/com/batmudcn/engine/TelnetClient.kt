package com.batmudcn.engine

import android.util.Log
import com.batmudcn.engine.TelnetConstants
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Telnet client for connecting to BatMUD server.
 * Handles IAC negotiation, MCCP compression filtering, and async read loop.
 *
 * Mirrors: mud_client.py MudClient + parser.py TextParser Telnet handling
 */
class TelnetClient(
    private val onData: suspend (ByteArray) -> Unit,
    private val onDisconnect: suspend () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        private const val TAG = "TelnetClient"
        private const val READ_BUF_SIZE = 4096
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private var running = false
    // Buffer for incomplete data (e.g., split IAC sequences across TCP packets)
    private val pendingData = ByteBuffer(1024)

    private var host: String = ""
    private var port: Int = 0

    val isConnected: Boolean
        get() = socket?.isConnected == true && !socket?.isClosed!!

    /**
     * Connect to the MUD server and start reading.
     */
    suspend fun connect(host: String, port: Int, timeoutMs: Int = 15000) {
        this.host = host
        this.port = port

        Log.i(TAG, "Connecting to $host:$port...")
        running = true

        withContext(Dispatchers.IO) {
            val sock = Socket()
            sock.soTimeout = 0 // no read timeout
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            socket = sock
            inputStream = sock.getInputStream()
            outputStream = sock.getOutputStream()
        }

        Log.i(TAG, "Connected to $host:$port")

        // Negotiate telnet options
        negotiateTelnetOptions()

        // Start read loop
        readJob = scope.launch(Dispatchers.IO) {
            readLoop()
        }
    }

    /**
     * Send a command string to the server.
     */
    suspend fun send(data: String) {
        if (!running) return
        withContext(Dispatchers.IO) {
            try {
                outputStream?.write(data.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Send error: ${e.message}")
            }
        }
    }

    /**
     * Disconnect from the server.
     */
    suspend fun disconnect() {
        Log.i(TAG, "Disconnecting...")
        running = false
        readJob?.cancel()
        withContext(Dispatchers.IO) {
            try {
                outputStream?.close()
                inputStream?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
        outputStream = null
        inputStream = null
        socket = null
    }

    /**
     * Send raw bytes for telnet negotiation responses.
     */
    private fun sendRaw(bytes: ByteArray) {
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (_: Exception) {}
    }

    /**
     * Respond to telnet option negotiations.
     * Mirrors standard telnet negotiation: refuse most options, accept SGA+EOR.
     */
    private fun negotiateTelnetOptions() {
        // Send initial negotiations
        // WILL SGA — suppress go-ahead
        sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.WILL, TelnetConstants.SGA))
        // WILL EOR — end of record
        sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.WILL, TelnetConstants.EOR))
        // WILL NAWS — window size (we'll send size later)
        sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.WILL, TelnetConstants.NAWS))
        // DO GMCP — we accept GMCP
        sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.DO, TelnetConstants.GMCP))
    }

    /**
     * Handle incoming IAC negotiation bytes.
     * Returns number of bytes consumed from the buffer.
     */
    private fun handleIAC(data: ByteArray, offset: Int): Int {
        if (offset + 2 >= data.size) return 0 // need at least 3 bytes

        val cmd = data[offset + 1]
        val opt = data[offset + 2]

        when (cmd) {
            TelnetConstants.DO -> {
                // Server wants us to DO something — refuse most
                val resp = when (opt) {
                    TelnetConstants.SGA, TelnetConstants.EOR, TelnetConstants.NAWS -> TelnetConstants.IAC to TelnetConstants.WILL // we already offered
                    else -> TelnetConstants.IAC to TelnetConstants.WONT
                }
                sendRaw(byteArrayOf(resp.first, resp.second, opt))
                return 3
            }
            TelnetConstants.DONT -> {
                // Server tells us DONT — acknowledge with WONT
                sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.WONT, opt))
                return 3
            }
            TelnetConstants.WILL -> {
                // Server WILL do something
                val resp = when (opt) {
                    TelnetConstants.SGA, TelnetConstants.EOR, TelnetConstants.GMCP, TelnetConstants.MSSP -> TelnetConstants.IAC to TelnetConstants.DO
                    else -> TelnetConstants.IAC to TelnetConstants.DONT
                }
                sendRaw(byteArrayOf(resp.first, resp.second, opt))
                return 3
            }
            TelnetConstants.WONT -> {
                // Server WONT do something — acknowledge with DONT
                sendRaw(byteArrayOf(TelnetConstants.IAC, TelnetConstants.DONT, opt))
                return 3
            }
            TelnetConstants.SB -> {
                // Sub-negotiation — find IAC SE end marker
                val endIdx = findIacSe(data, offset + 3)
                if (endIdx != -1) {
                    return (endIdx + 2) - offset // consume including IAC SE
                }
                return 0 // incomplete, wait for more data
            }
            else -> return 1 // skip IAC alone
        }
    }

    private fun findIacSe(data: ByteArray, start: Int): Int {
        var i = start
        while (i < data.size - 1) {
            if (data[i] == TelnetConstants.IAC && data[i + 1] == TelnetConstants.SE) {
                return i
            }
            i++
        }
        return -1
    }

    /**
     * Filter MCCP compressed data out of the stream.
     * Mirrors Python mud_client.py _filter_compress.
     */
    private fun filterCompress(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        var outPos = 0
        var i = 0
        val n = data.size

        while (i < n) {
            if (data[i] == TelnetConstants.IAC && i + 2 < n) {
                val cmd = data[i + 1]
                val opt = data[i + 2]

                if (opt in TelnetConstants.COMPRESS_OPTIONS) {
                    if (cmd == TelnetConstants.SB) {
                        val end = findIacSe(data, i + 3)
                        if (end != -1) {
                            i = end + 2
                            continue
                        }
                    }
                    i += 3
                    continue
                }

                if (cmd == TelnetConstants.SB) {
                    val end = findIacSe(data, i + 3)
                    if (end != -1) {
                        // Skip entire non-compress SB sub-negotiation
                        i = end + 2
                        continue
                    }
                }

                // Skip 3-byte IAC sequence
                i += 3
            } else {
                result[outPos++] = data[i]
                i++
            }
        }
        return result.copyOf(outPos)
    }

    /**
     * Main read loop — reads data, filters telnet/MCCP, emits to callback.
     */
    private suspend fun readLoop() {
        val buffer = ByteArray(READ_BUF_SIZE)

        try {
            while (running && scope.isActive) {
                val stream = inputStream ?: break

                // Read from socket (blocking call on IO dispatcher)
                val bytesRead = withContext(Dispatchers.IO) {
                    try {
                        stream.read(buffer)
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "Read error: ${e.message}")
                        -1
                    }
                }

                if (bytesRead == -1) {
                    Log.i(TAG, "Server closed connection")
                    break
                }

                if (bytesRead > 0) {
                    var rawData = buffer.copyOf(bytesRead)

                    // Prepend any pending data from previous incomplete IAC
                    if (pendingData.size > 0) {
                        val pendingSize = pendingData.size
                        val pending = pendingData.consume()
                        val combined = ByteArray(pendingSize + rawData.size)
                        System.arraycopy(pending, 0, combined, 0, pendingSize)
                        System.arraycopy(rawData, 0, combined, pendingSize, rawData.size)
                        rawData = combined
                    }

                    // Log raw data
                    Log.d(TAG, "[RAW_IN] len=$bytesRead hex=${rawData.joinToString(" ") { "%02x".format(it) }}")

                    // Process data: strip IAC negotiation, filter MCCP
                    val processed = processIncomingData(rawData)

                    // Filter telnet negotiations out
                    val cleaned = filterTelnet(processed)

                    if (cleaned.isNotEmpty()) {
                        onData(cleaned)
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Read loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Read loop error: ${e.message}", e)
        } finally {
            if (running) {
                running = false
                onDisconnect()
            }
        }
    }

    /**
     * Process incoming data: handle IAC negotiation and MCCP filtering.
     * Returns the "clean" game text data.
     */
    private fun processIncomingData(data: ByteArray): ByteArray {
        // First pass: handle IAC negotiation (mutates by sending responses)
        var pos = 0
        val textParts = mutableListOf<ByteArray>()
        var lastTextStart = 0
        var incomplete = false

        while (pos < data.size) {
            if (data[pos] == TelnetConstants.IAC && pos + 1 < data.size) {
                // Found IAC — save preceding text
                if (pos > lastTextStart) {
                    textParts.add(data.copyOfRange(lastTextStart, pos))
                }
                val consumed = handleIAC(data, pos)
                if (consumed == 0) {
                    // Incomplete IAC sequence — save to pending buffer
                    pendingData.append(data.copyOfRange(pos, data.size))
                    incomplete = true
                    break
                }
                pos += consumed
                lastTextStart = pos
            } else {
                pos++
            }
        }

        // Remaining text after last IAC (only if no incomplete sequence)
        if (!incomplete && lastTextStart < data.size) {
            textParts.add(data.copyOfRange(lastTextStart, data.size))
        }

        // Reassemble text parts
        val totalLen = textParts.sumOf { it.size }
        if (totalLen == 0) return ByteArray(0)
        val assembled = ByteArray(totalLen)
        var offset = 0
        for (part in textParts) {
            System.arraycopy(part, 0, assembled, offset, part.size)
            offset += part.size
        }

        // Second pass: filter MCCP compressed data
        return filterCompress(assembled)
    }

    /**
     * Strip remaining telnet negotiation bytes from the data stream.
     */
    private fun filterTelnet(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        var outPos = 0
        var i = 0

        while (i < data.size) {
            if (data[i] == TelnetConstants.IAC && i + 2 < data.size) {
                val cmd = data[i + 1]
                if (cmd == TelnetConstants.SB) {
                    val end = findIacSe(data, i + 3)
                    if (end != -1) {
                        i = end + 2
                        continue
                    }
                }
                // Skip regular IAC command
                i += 3
                continue
            }
            result[outPos++] = data[i]
            i++
        }
        return result.copyOf(outPos)
    }

    /**
     * Attempt reconnection after connection loss.
     */
    suspend fun tryReconnect(): Boolean {
        Log.i(TAG, "Attempting reconnect...")
        for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
            Log.i(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS...")
            try {
                disconnect()
                delay(RECONNECT_DELAY_MS)
                connect(host, port)
                Log.i(TAG, "Reconnected successfully")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt $attempt failed: ${e.message}")
            }
        }
        return false
    }
}
