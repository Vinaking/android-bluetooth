package com.ichiisoft.android_bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity(), AdapterView.OnItemClickListener {
    private lateinit var btnStartScan: Button
    private lateinit var btnStopScan: Button
    private lateinit var lvDevices: ListView
    private lateinit var txtLog: TextView

    private var isDiscoverable = false
    private var stopScanning = false

    private lateinit var mBluetoothAdapter: BluetoothAdapter

    private lateinit var deviceAdapter: ArrayAdapter<*>
    private var arrayList = ArrayList<String>()
    private var deviceData = ArrayList<BluetoothDevice>()

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()

        btnStartScan = findViewById(R.id.btnStartScan)
        btnStopScan = findViewById(R.id.btnStopScan)

        lvDevices = findViewById(R.id.deviceList)
        lvDevices.onItemClickListener = this
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayList)
        lvDevices.adapter = deviceAdapter

        txtLog = findViewById(R.id.txtLog)

        mBluetoothAdapter = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!mBluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableBtIntent)
        }

        btnStartScan.setOnClickListener {
            startScanClick()
        }

        btnStopScan.setOnClickListener {
            stopScanClick()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    // ---- Event ----

    private fun startScanClick() {
        stopScanning = false

        if (!mBluetoothAdapter.isEnabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            mBluetoothAdapter.enable()
        }

        if (!isDiscoverable) {
            isDiscoverable = true
            makeDiscoverable()
        }

        if (!mBluetoothAdapter.isDiscovering) {
            startBluetoothScan()
        }
    }

    private fun stopScanClick() {
        stopScanning = true
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mBluetoothAdapter.cancelDiscovery()
    }

    override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        connectBluetoothDevice(p2)
    }

    private fun startBluetoothScan() {

        deviceData.clear()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val hasDiscoveryStarted: Boolean = mBluetoothAdapter.startDiscovery()
        Log.d("BLUETOOTHLOG", "Discovery started: $hasDiscoveryStarted")

        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)
    }

    private fun makeDiscoverable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val requestCode = 1
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1000000000)
        startActivityForResult(discoverableIntent, requestCode)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                val deviceName = device?.name
                if (deviceName != null) {
                    Log.d("BLUETOOTHLOG", device.name)

                    //device data is given to the array loader
                    loadDevicesToList(device)
                }
            }
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                if (!stopScanning) {
                    //if bluetooth scan has finished it restarts the process
                    Log.d("BLUETOOTHLOG", "Discovery has finished")
                    startBluetoothScan()
                }
            }
        }
    }

    private fun loadDevicesToList(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (!arrayList.contains("${device.name}\n${device.address}")) {
            deviceData.add(device)
            arrayList.add("${device.name}\n${device.address}")
            deviceAdapter.notifyDataSetChanged()
        }
    }

    private fun connectBluetoothDevice(i: Int) {
        val device = deviceData[i]
        stopScanning = true
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mBluetoothAdapter.cancelDiscovery()
        Log.d("BLUETOOTHLOG", "Pairing with: " + device.name)

        //bonded is 11, not bonded is 10 ????? bruh
        val isBonded = device.bondState
        Log.d("Bond state", isBonded.toString())

        //device is paired
        device.createBond()

        //waits for user pairing confirmation
        val handler = Handler()
        handler.postDelayed(Runnable { //device.setPairingConfirmation(true);
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return@Runnable
            }
            val devicePairCheck = device.bondState
            Log.d("Bond state", devicePairCheck.toString())

            //if device is not paired scan again
            if (devicePairCheck == 10) {
                startBluetoothScan()
            } else {
                txtLog.text = "Device paired"
            }
        }, 5000) //10 seconds
    }

    private fun checkPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN
                ), 1
            )
        }
    }

}