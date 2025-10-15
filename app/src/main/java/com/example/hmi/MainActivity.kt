package com.example.hmi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.*

class MainActivity : ComponentActivity() {

    private val deviceAddress = "00:11:22:33:44:55" // Reemplaza con la MAC real de tu dispositivo Bluetooth
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID estándar SPP

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothFlowApp()
        }
    }

    @Composable
    fun BluetoothFlowApp() {
        val entriesInvasivo = remember { mutableStateListOf<Entry>() }
        val entriesNoInvasivo = remember { mutableStateListOf<Entry>() }

        var flujoNoInvasivo by remember { mutableStateOf(0.0) }
        var flujoInvasivo by remember { mutableStateOf(0.0) }
        var temperatura by remember { mutableStateOf(36.5) }  // temperatura fija ejemplo

        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                // Si quieres usar Bluetooth real, reemplaza la llamada aquí
                // por connectAndReadBluetoothData() y procesa la entrada real.
                connectAndReadData { flujoNo, flujoIn, temp, index ->
                    flujoNoInvasivo = flujoNo
                    flujoInvasivo = flujoIn
                    temperatura = temp

                    entriesNoInvasivo.add(Entry(index.toFloat(), flujoNo.toFloat()))
                    entriesInvasivo.add(Entry(index.toFloat(), flujoIn.toFloat()))

                    // Mantener solo datos hasta 300 segundos (5 minutos)
                    while (entriesNoInvasivo.isNotEmpty() && entriesNoInvasivo.first().x < (index - 300)) {
                        entriesNoInvasivo.removeAt(0)
                    }
                    while (entriesInvasivo.isNotEmpty() && entriesInvasivo.first().x < (index - 300)) {
                        entriesInvasivo.removeAt(0)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Volumetric Flow Rate of Sensors:",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF800080), // Morado
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Non-Invasive", style = MaterialTheme.typography.titleMedium)
                            Text("%.2f m³/s".format(flujoNoInvasivo), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column {
                            Text("Invasive", style = MaterialTheme.typography.titleMedium)
                            Text("%.2f m³/s".format(flujoInvasivo), style = MaterialTheme.typography.headlineSmall)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Temperature: %.1f °C".format(temperatura), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Volumetric Flow Rate vs Time", style = MaterialTheme.typography.titleMedium)

            LineChartView(entriesInvasivo, entriesNoInvasivo)
        }
    }

    @Composable
    fun LineChartView(entriesInvasivo: List<Entry>, entriesNoInvasivo: List<Entry>) {
        val context = LocalContext.current

        AndroidView(
            factory = {
                LineChart(context).apply {
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    axisRight.isEnabled = false
                    description = Description().apply { text = "Time (s)" }
                    legend.isEnabled = true
                    animateX(500)

                    xAxis.axisMinimum = 0f
                    xAxis.axisMaximum = 300f  // Máximo 5 minutos = 300 segundos
                    xAxis.granularity = 30f   // marcas cada 30 segundos
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(8.dp),
            update = { chart ->
                val dataSetInvasivo = LineDataSet(entriesInvasivo, "Invasive").apply {
                    color = android.graphics.Color.RED
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                }

                val dataSetNoInvasivo = LineDataSet(entriesNoInvasivo, "Non-Invasive").apply {
                    color = android.graphics.Color.GREEN
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                }

                chart.data = LineData(dataSetInvasivo, dataSetNoInvasivo)
                chart.invalidate()
            }
        )
    }

    // Simulación de lectura de datos (tu lista de datos invasivos)
    private suspend fun connectAndReadData(onDataReceived: (Double, Double, Double, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val datosInvasivo = listOf(
                13, 14, 14, 13, 14, 13, 14, 13, 14, 13, 14, 14, 14, 13, 13, 14, 14, 13, 13, 14,
                13, 14, 14, 13, 15, 13, 14, 14, 13, 15, 16, 13, 13, 14, 14, 13, 13, 15, 15, 14,
                13, 14, 14, 13, 13, 14, 13, 14, 13, 14, 13, 14, 13, 14, 13, 13, 14, 14, 14, 14,
                13, 13, 14, 14, 13, 14, 13, 14, 14, 13, 14, 14, 14, 13, 14, 14, 14, 13, 14, 14,
                15, 15, 15, 13, 14, 13, 14, 13, 14, 13, 15, 14, 13, 14, 14, 14, 13, 14, 15, 13,
                14, 13, 13, 14, 14, 14, 14, 13, 15, 13, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14,
                14, 14, 14, 14, 14, 13, 15, 14, 15, 13, 14, 15, 13, 14, 15, 14, 13, 14, 14, 13,
                14, 13, 14, 13, 14, 13, 15, 14, 14, 14, 14, 14, 13, 14, 13, 14, 14, 14, 14, 13,
                14, 14, 14, 14, 13, 14, 14, 14, 14, 15, 14, 15, 13, 14, 14, 15, 14, 13, 13, 15,
                14, 14, 14, 15, 14, 14, 13, 13, 14, 13, 14, 14, 14, 13, 15, 14, 14, 13, 14, 14,
                14, 15, 15, 14, 15, 15, 14, 14, 14, 13, 15, 14, 14, 14, 13, 14, 13, 14, 14, 13,
                14, 13, 14, 14, 13, 14, 15, 14, 14, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
                14, 13, 15, 14, 14, 14, 14, 14, 15, 14, 13, 14, 13, 14, 14, 14, 14, 13, 14, 14,
                13, 14, 13, 14, 14, 14, 14, 13, 14, 14, 14, 14, 13, 14, 14, 14, 14, 15, 13, 15,
                14, 14, 14, 13, 14, 14, 13, 14, 13, 15, 13, 14, 15, 13, 15, 14, 14, 13, 14, 14,
                14, 14, 14, 13, 15, 14, 13, 15, 14, 13, 14, 13, 15, 13, 15, 14, 15, 15, 14, 13,
                15, 14, 14, 14, 14, 14, 14, 14, 14, 14, 13, 13, 14, 14, 14, 13, 14, 14, 14, 14,
                14, 14, 14, 14, 14, 13, 14, 14, 14, 14, 15, 14, 15, 14, 14, 15, 13, 14, 14, 13,
                15, 14, 14, 14, 14, 15, 14, 14, 14, 14, 14, 14, 14, 13, 15, 13, 15, 14, 14, 14,
                14
            )

            for (i in datosInvasivo.indices) {
                val invasivo = datosInvasivo[i].toDouble()
                val noInvasivo = 0.0  // Siempre 0
                val temp = 27.0 // temperatura fija

                delay(1000) // simula 1 lectura por segundo

                onDataReceived(noInvasivo, invasivo, temp, i)
            }
        }
    }

    // Ejemplo básico para conexión Bluetooth (no usada en este código, pero te la dejo para referencia)
    private fun connectBluetoothDevice(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

        return try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
