package com.example.bluetoothledcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class NavesActivity : AppCompatActivity() {

    private val TAG = "NavesActivity"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var selectedGattCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 15000 // 15 segundos de escaneo

    private var isScanning = false
    private val devices = mutableListOf<BluetoothDevice>()

    private lateinit var btnConnectBle: Button
    private lateinit var btnEncenderLed: Button
    private lateinit var btnApagarLed: Button

    // Permisos necesarios para BLE en Android 12+
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    // Launcher para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            // Todos los permisos concedidos
            initializeBluetoothAdapter()
            startBleScan()
        } else {
            Toast.makeText(
                this,
                "Se necesitan permisos para usar BLE",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_naves)

        btnConnectBle = findViewById(R.id.btnConnectBle)
        btnEncenderLed = findViewById(R.id.btnEncenderLed)
        btnApagarLed = findViewById(R.id.btnApagarLed)

        btnConnectBle.setOnClickListener {
            disconnectFromDevice()
            if (checkAndRequestPermissions()) {
                startBleScan()
            }
        }

        btnEncenderLed.setOnClickListener {
            sendCommand("1")
        }

        btnApagarLed.setOnClickListener {
            sendCommand("0")
        }

        initializeBluetoothAdapter()
    }

    private fun initializeBluetoothAdapter() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

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
        // Verificar ubicación
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setTitle("Ubicación requerida")
                .setMessage("La ubicación debe estar activada para escanear dispositivos Bluetooth LE")
                .setPositiveButton("Configuración") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return false
        }

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
    private fun startBleScan() {
        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkAndRequestPermissions()) {
            return
        }

        if (isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            btnConnectBle.text = "Buscar dispositivos BLE"
            Toast.makeText(this, "Escaneo detenido", Toast.LENGTH_SHORT).show()
            return
        }

        // Limpiar lista anterior
        devices.clear()

        // Configurar filtros y configuración de escaneo para mejorar resultados
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Iniciar escaneo con configuración
        Toast.makeText(this, "Buscando dispositivos BLE...", Toast.LENGTH_SHORT).show()
        btnConnectBle.text = "Detener búsqueda"

        // Reiniciar el adaptador Bluetooth antes de escanear (puede ayudar en algunos dispositivos)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            handler.postDelayed({
                try {
                    bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
                    isScanning = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar escaneo: ${e.message}")
                    Toast.makeText(this, "Error al iniciar escaneo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, 500) // Pequeño retraso para asegurar que el adaptador esté listo
        } else {
            try {
                bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
                isScanning = true
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar escaneo: ${e.message}")
                Toast.makeText(this, "Error al iniciar escaneo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Detener el escaneo y mostrar diálogo después de un tiempo
        handler.postDelayed({
            if (isScanning) {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                btnConnectBle.text = "Buscar dispositivos BLE"

                // Mostrar diálogo con dispositivos encontrados
                showDeviceSelectionDialog()
            }
        }, SCAN_PERIOD)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                // Verificar si ya tenemos este dispositivo en la lista
                if (!devices.contains(device)) {
                    devices.add(device)
                    Log.d(TAG, "Dispositivo encontrado: ${device.name ?: "Desconocido"}, Address: ${device.address}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Escaneo BLE fallido con código: $errorCode")
            runOnUiThread {
                isScanning = false
                btnConnectBle.text = "Buscar dispositivos BLE"
                when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                        Toast.makeText(applicationContext, "Error: Escaneo ya iniciado", Toast.LENGTH_SHORT).show()
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                        Toast.makeText(applicationContext, "Error: Registro de aplicación fallido", Toast.LENGTH_SHORT).show()
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                        Toast.makeText(applicationContext, "Error: BLE no soportado", Toast.LENGTH_SHORT).show()
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                        Toast.makeText(applicationContext, "Error interno de escaneo", Toast.LENGTH_SHORT).show()
                    else ->
                        Toast.makeText(applicationContext, "Error de escaneo: $errorCode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceSelectionDialog() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "No se encontraron dispositivos BLE", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = devices.map { device ->
            device.name ?: "Dispositivo desconocido (${device.address})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Selecciona un dispositivo BLE")
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

        // Intentar conectar con auto-connect = false para una conexión más directa
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        // Establecer un timeout para la conexión
        handler.postDelayed({
            if (bluetoothGatt != null) {
                val isConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
                if (!isConnected) {
                    Log.e(TAG, "Timeout de conexión")
                    Toast.makeText(this, "Timeout de conexión. Intentando de nuevo...", Toast.LENGTH_SHORT).show()
                    // Reintentar la conexión
                    disconnectFromDevice()
                    bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                }
            }
        }, 10000) // 10 segundos de timeout
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceInfo = "${gatt.device.name ?: gatt.device.address}"

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado a $deviceInfo (Estado: $status)")
                    runOnUiThread {
                        Toast.makeText(this@NavesActivity, "Conectado a $deviceInfo", Toast.LENGTH_SHORT).show()
                    }
                    // Pequeño delay antes de descubrir servicios para dar tiempo al dispositivo
                    handler.postDelayed({
                        try {
                            gatt.discoverServices()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al descubrir servicios: ${e.message}")
                        }
                    }, 1000)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado de $deviceInfo (Estado: $status)")
                    runOnUiThread {
                        Toast.makeText(this@NavesActivity, "Desconectado de $deviceInfo", Toast.LENGTH_SHORT).show()
                    }
                    closeGatt(gatt)
                }
            }

            // Manejar errores de conexión
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Error en conexión GATT: $status")
                runOnUiThread {
                    Toast.makeText(this@NavesActivity, "Error de conexión: $status", Toast.LENGTH_SHORT).show()
                }
                closeGatt(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val deviceInfo = "${gatt.device.name ?: gatt.device.address}"

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Servicios descubiertos en $deviceInfo")

                // Buscar el servicio y característica adecuados para comunicación
                findCharacteristicForCommunication(gatt)

                runOnUiThread {
                    Toast.makeText(this@NavesActivity, "Servicios descubiertos en $deviceInfo", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "Error al descubrir servicios: $status")
                runOnUiThread {
                    Toast.makeText(this@NavesActivity, "Error al descubrir servicios: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Escritura exitosa en característica ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Error al escribir en característica: $status")
                runOnUiThread {
                    Toast.makeText(this@NavesActivity, "Error al enviar comando: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findCharacteristicForCommunication(gatt: BluetoothGatt) {
        // Lista de UUIDs comunes para servicios de comunicación serial en BLE
        val potentialServiceUuids = listOf(
            // UUID común para servicios UART/Serial
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            // UUID para dispositivos HC-05 en modo BLE
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
            // SPP UUID (Serial Port Profile - usado por algunos HC-05)
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        )

        // Lista de UUIDs comunes para características de escritura
        val potentialCharacteristicUuids = listOf(
            // Característica típica para escritura UART
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            // Característica alternativa para HC-05
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        )

        // Imprimir todos los servicios y características para depuración
        gatt.services.forEach { service ->
            Log.d(TAG, "Servicio: ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                Log.d(TAG, "    Característica: ${characteristic.uuid}, propiedades: ${characteristic.properties}")
            }
        }

        // Intentar encontrar una característica adecuada para escribir
        for (serviceUuid in potentialServiceUuids) {
            val service = gatt.getService(serviceUuid)
            if (service != null) {
                Log.d(TAG, "Servicio encontrado: $serviceUuid")

                // Primero intentar con UUIDs conocidos
                for (charUuid in potentialCharacteristicUuids) {
                    val characteristic = service.getCharacteristic(charUuid)
                    if (characteristic != null &&
                        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)) {
                        selectedGattCharacteristic = characteristic
                        Log.d(TAG, "¡Característica para escritura encontrada! UUID: $charUuid")
                        return
                    }
                }

                // Si no se encuentra, buscar cualquier característica con propiedad de escritura
                for (characteristic in service.characteristics) {
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                        selectedGattCharacteristic = characteristic
                        Log.d(TAG, "¡Característica para escritura encontrada! UUID: ${characteristic.uuid}")
                        return
                    }
                }
            }
        }

        // Si no encontramos en los servicios conocidos, buscar en todos los servicios
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    selectedGattCharacteristic = characteristic
                    Log.d(TAG, "¡Característica para escritura encontrada (servicio general)! UUID: ${characteristic.uuid}")
                    return
                }
            }
        }

        Log.e(TAG, "No se encontró una característica adecuada para escribir")
        runOnUiThread {
            Toast.makeText(this, "No se encontró una característica para enviar comandos", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(command: String) {
        val gatt = bluetoothGatt
        val characteristic = selectedGattCharacteristic

        if (gatt == null) {
            Toast.makeText(this, "No hay conexión activa", Toast.LENGTH_SHORT).show()
            return
        }

        if (characteristic == null) {
            Toast.makeText(this, "No se encontró una característica para enviar comandos", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            characteristic.value = command.toByteArray()

            // Determinar el tipo de escritura según las propiedades de la característica
            val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            characteristic.writeType = writeType
            val success = gatt.writeCharacteristic(characteristic)

            if (success) {
                Toast.makeText(this, "Enviando comando: $command", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Comando enviado: $command")
            } else {
                Toast.makeText(this, "Error al enviar comando", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error al enviar comando: writeCharacteristic devolvió false")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al enviar comando: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error al enviar comando", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {
        try {
            gatt.close()
            if (gatt == bluetoothGatt) {
                bluetoothGatt = null
                selectedGattCharacteristic = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar GATT: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectFromDevice() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                // No cerramos el GATT inmediatamente para permitir que
                // el callback onConnectionStateChange maneje la desconexión
                handler.postDelayed({
                    closeGatt(gatt)
                }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "Error al desconectar: ${e.message}")
                closeGatt(gatt)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener escaneo si está activo
        if (isScanning) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
            }
        }
        disconnectFromDevice()
    }

    override fun onPause() {
        super.onPause()
        // Asegurarse de que el escaneo se detenga cuando la actividad pase a segundo plano
        if (isScanning) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                btnConnectBle.text = "Buscar dispositivos BLE"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reinicializar el adaptador Bluetooth cuando la actividad vuelve al primer plano
        initializeBluetoothAdapter()
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}