package com.example.hmi

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothConnection(private val device: BluetoothDevice) {

    companion object {
        private val SERIAL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    @Volatile private var keepReading = false

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    @SuppressLint("MissingPermission")
    fun connect(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        thread {
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SERIAL_UUID)
                s.connect()
                socket = s
                onResult(true, null)
            } catch (_: SecurityException) {
                onResult(false, "Missing Bluetooth permissions (CONNECT/SCAN)")
            } catch (e: IOException) {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                onResult(false, e.message ?: "Connection error")
            }
        }
    }

    /**
     * Reads lines and emits (flow, temp) pairs.
     * Accepted per-line formats (must contain *both* values):
     *   - CSV: "flow,temp"  (recommended)
     *   - Labeled: "F=12.3,T=25.6" or "FLOW:12.3 TEMP:25.6"
     *   - With timestamp: "ts,flow,temp" → uses the last two numerics
     *
     * If only one numeric is present, the line is ignored (no callback).
     */
    fun startReading(onSample: (inv: Double, temp: Double) -> Unit) {
        stopReading()
        val input = socket?.inputStream ?: return
        keepReading = true
        readerThread = thread(start = true) {
            val reader = BufferedReader(InputStreamReader(input))
            try {
                while (keepReading && !Thread.currentThread().isInterrupted) {
                    val raw = reader.readLine() ?: break
                    parseLine(raw)?.let { (flow, temp) ->
                        onSample(flow, temp)
                    }
                }
            } catch (_: IOException) {
                // socket closed / IO error
            } finally {
                keepReading = false
            }
        }
    }

    fun stopReading() {
        keepReading = false
        readerThread?.interrupt()
        readerThread = null
    }

    fun disconnect() {
        stopReading()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    /** Do not fabricate temperature from flow. Require both. */
    private fun parseLine(lineRaw: String): Pair<Double, Double>? {
        val line = lineRaw.trim()
        if (line.isEmpty()) return null

        // 1) Labeled tokens: F=..., T=... (or FLOW:, TEMP:)
        run {
            var f: Double? = null
            var t: Double? = null
            val tokens = line.split(',', ';', ' ', '\t')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (tok in tokens) {
                val p = tok.split('=', ':')
                if (p.size == 2) {
                    val key = p[0].trim().uppercase()
                    val value = p[1].trim().toDoubleOrNull()
                    if (value != null) {
                        when (key) {
                            "F", "FLOW" -> f = value
                            "T", "TEMP", "TEMPERATURE" -> t = value
                        }
                    }
                }
            }
            if (f != null && t != null) return f!! to t!!
        }

        // 2) Plain numerics: need at least two numbers
        val nums = extractNumerics(line)
        if (nums.size >= 2) {
            return if (nums.size == 3) {
                // e.g., "timestamp,flow,temp"
                nums[1] to nums[2]
            } else {
                // take the first two numbers
                nums[0] to nums[1]
            }
        }

        // Only one numeric → ignore this line
        return null
    }

    private fun extractNumerics(s: String): List<Double> {
        val tokens = s.split(',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val out = ArrayList<Double>(tokens.size)
        for (t in tokens) {
            t.toDoubleOrNull()?.let { out.add(it) }
        }
        return out
    }
}
