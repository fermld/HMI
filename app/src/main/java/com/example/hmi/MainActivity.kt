package com.example.hmi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class MainActivity : ComponentActivity() {

    // ⬇️ Replace with your target MAC if needed
    private val deviceAddress = "00:4B:12:24:AE:CA"
    private var bt: BluetoothConnection? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissionsIfNeeded()
        setContent { AppUI() }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    @Composable
    fun AppUI() {
        val entries = remember { mutableStateListOf<Entry>() }

        var flujo by remember { mutableStateOf(0.0) }
        var temperatura by remember { mutableStateOf(0.0) }
        var conectado by remember { mutableStateOf(false) }
        var seconds by remember { mutableStateOf(0) }

        val ctx = LocalContext.current

        fun appendPoint(value: Double) {
            entries.add(Entry(seconds.toFloat(), value.toFloat()))
            seconds += 1
            while (entries.size > 300) entries.removeAt(0) // keep last 5 min @1Hz
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Volumetric Flow Rate",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF800080),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        val adapter = getBluetoothAdapter()
                        if (adapter == null || !adapter.isEnabled) {
                            Toast.makeText(ctx, "Bluetooth off or unavailable", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val needConnect = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            val needScan = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.BLUETOOTH_SCAN
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (needConnect || needScan) {
                                permissionLauncher.launch(arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                ))
                                return@Button
                            }
                        }
                        try {
                            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
                            bt = BluetoothConnection(device)
                            bt?.connect { success, err ->
                                runOnUiThread {
                                    if (success) {
                                        conectado = true
                                        Toast.makeText(ctx, "✅ Connected to ESP32", Toast.LENGTH_SHORT).show()
                                        bt?.startReading { inv, temp ->
                                            runOnUiThread {
                                                flujo = inv
                                                temperatura = temp
                                                appendPoint(inv)   // ✅ plot ONLY flow
                                            }
                                        }
                                    } else {
                                        Toast.makeText(ctx, "❌ $err", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (_: SecurityException) {
                            Toast.makeText(ctx, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(ctx, "Invalid MAC address", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !conectado
                ) { Text("Conectar") }

                Button(
                    onClick = {
                        bt?.disconnect()
                        conectado = false
                        Toast.makeText(ctx, "🔌 Disconnected", Toast.LENGTH_SHORT).show()
                    },
                    enabled = conectado
                ) { Text("Desconectar") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (conectado) "Estado: Conectado" else "Estado: Desconectado",
                color = if (conectado) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Flow (Invasive)", style = MaterialTheme.typography.titleMedium)
                    Text("%.2f m³/s".format(flujo), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Temperature: %.1f °C".format(temperatura), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Flow vs Time", style = MaterialTheme.typography.titleMedium)
            LineChartSingle(entries)
        }
    }

    @Composable
    fun LineChartSingle(entries: List<Entry>) {
        AndroidView(
            factory = { ctx ->
                LineChart(ctx).apply {
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    axisRight.isEnabled = false
                    description = Description().apply { text = "Time (s)" }
                    legend.isEnabled = true
                    xAxis.granularity = 1f
                }
            },
            modifier = Modifier.fillMaxWidth().height(300.dp),
            update = { chart ->
                val dataSet = LineDataSet(entries, "Invasive Flow").apply {
                    color = android.graphics.Color.RED
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                }
                chart.data = LineData(dataSet)
                chart.invalidate()
            }
        )
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val toRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) toRequest += Manifest.permission.BLUETOOTH_CONNECT
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) toRequest += Manifest.permission.BLUETOOTH_SCAN
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) toRequest += Manifest.permission.BLUETOOTH
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) toRequest += Manifest.permission.BLUETOOTH_ADMIN
        }
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
    }

    override fun onDestroy() {
        super.onDestroy()
        bt?.disconnect()
    }
}
