package com.example.hmi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothConnection(private val device: BluetoothDevice) {

    companion object {
        // UUID estándar para comunicación SPP (HC-05/HC-06)
        private val SERIAL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothSocket: BluetoothSocket? = null

    /**
     * Intenta conectar al dispositivo Bluetooth en un hilo de fondo.
     * Llama al callback onResult con true si se conecta correctamente.
     */
    fun connect(onResult: (Boolean, String?) -> Unit) {
        Thread {
            try {
                // Cancelar cualquier búsqueda activa (recomendado antes de conectar)
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

                // Crear socket RFCOMM para el dispositivo y UUID
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SERIAL_UUID)

                bluetoothSocket?.connect()

                println("✅ Bluetooth conectado correctamente.")
                onResult(true, null)
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    bluetoothSocket?.close()
                } catch (_: Exception) {}
                println("❌ Error al conectar Bluetooth: ${e.message}")
                onResult(false, e.message)
            }
        }.start()
    }

    /**
     * Devuelve el InputStream para leer datos desde el dispositivo.
     */
    fun getInputStream(): InputStream? {
        return bluetoothSocket?.inputStream
    }

    /**
     * Envía datos como string al dispositivo Bluetooth.
     */
    fun sendData(data: String) {
        Thread {
            try {
                bluetoothSocket?.outputStream?.write(data.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Cierra la conexión Bluetooth.
     */
    fun disconnect() {
        try {
            bluetoothSocket?.close()
            println("🔌 Bluetooth desconectado.")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Verifica si hay una conexión activa.
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
}
