package com.example.bluetoothledcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 15000 // 15 segundos de escaneo

    private var isScanning = false
    private val devices = mutableListOf<BluetoothDevice>()

    private lateinit var btnConnectBluetooth: Button
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button

    // UUID para SPP (Serial Port Profile)
    private val UUID_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Permisos necesarios para Bluetooth Classic
    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Launcher para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            // Todos los permisos concedidos
            initializeBluetoothAdapter()
            startBluetoothDiscovery()
        } else {
            Toast.makeText(
                this,
                "Se necesitan permisos para usar Bluetooth",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnectBluetooth = findViewById(R.id.btnConnectBluetooth)
        btnOn = findViewById(R.id.btnOn)
        btnOff = findViewById(R.id.btnOff)

        btnConnectBluetooth.setOnClickListener {
            disconnectFromDevice()
            if (checkAndRequestPermissions()) {
                startBluetoothDiscovery()
            }
        }

        btnOn.setOnClickListener {
            sendCommand("1")
        }

        btnOff.setOnClickListener {
            sendCommand("0")
        }

        initializeBluetoothAdapter()
    }

    private fun initializeBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar si Bluetooth está habilitado
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this, "Por favor, activa Bluetooth", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        // Verificar permisos
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions)
            return false
        }

        return true
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkAndRequestPermissions()) {
            return
        }

        if (isScanning) {
            bluetoothAdapter?.cancelDiscovery()
            isScanning = false
            btnConnectBluetooth.text = "Buscar dispositivos"
            Toast.makeText(this, "Escaneo detenido", Toast.LENGTH_SHORT).show()
            return
        }

        // Limpiar lista anterior
        devices.clear()

        // Registrar el BroadcastReceiver para dispositivos encontrados
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        // Iniciar el descubrimiento
        Toast.makeText(this, "Buscando dispositivos Bluetooth...", Toast.LENGTH_SHORT).show()
        btnConnectBluetooth.text = "Detener búsqueda"
        bluetoothAdapter?.startDiscovery()
        isScanning = true

        // Detener el escaneo después de un tiempo
        handler.postDelayed({
            if (isScanning) {
                bluetoothAdapter?.cancelDiscovery()
                isScanning = false
                btnConnectBluetooth.text = "Buscar dispositivos"
                showDeviceSelectionDialog()
            }
        }, SCAN_PERIOD)
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent?.action ?: return
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (!devices.contains(it)) {
                        devices.add(it)
                        Log.d(TAG, "Dispositivo encontrado: ${it.name ?: "Desconocido"}, Address: ${it.address}")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceSelectionDialog() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "No se encontraron dispositivos", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = devices.map { device ->
            device.name ?: "Dispositivo desconocido (${device.address})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Selecciona un dispositivo")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices[which]
                connectToDevice(selectedDevice)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        disconnectFromDevice()

        Toast.makeText(this, "Conectando a ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()

        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(UUID_SERIAL_PORT)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream

            Toast.makeText(this, "Conectado a ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error al conectar con ${device.name ?: device.address}", e)
            Toast.makeText(this, "Error al conectar con ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendCommand(command: String) {
        try {
            outputStream?.write(command.toByteArray())
            Toast.makeText(this, "Comando enviado: $command", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Error al enviar comando", e)
            Toast.makeText(this, "Error al enviar comando", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectFromDevice() {
        try {
            bluetoothSocket?.close()
            outputStream?.close()
            bluetoothSocket = null
            outputStream = null
        } catch (e: IOException) {
            Log.e(TAG, "Error al desconectar", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromDevice()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error al desregistrar el BroadcastReceiver", e)
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}