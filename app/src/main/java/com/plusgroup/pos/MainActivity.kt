package com.plusgroup.pos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.plusgroup.pos.databinding.ActivityMainBinding
import com.plusgroup.pos.printer.PrinterManager
import com.plusgroup.pos.util.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printerManager: PrinterManager
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        printerManager = PrinterManager(this)

        requestBluetoothPermissionsIfNeeded()
        setupPrinterUi()
        connectPrinter()

        binding.btnTestPrint.setOnClickListener {
            if (printerManager.isReady()) {
                printerManager.printTestReceipt()
            } else {
                Toast.makeText(this, "Enprimant pa konekte toujou", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChoosePrinter.setOnClickListener {
            showBluetoothPrinterPicker()
        }

        binding.btnLogout.setOnClickListener {
            session.clear()
            val loginIntent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(loginIntent)
            finish()
        }
    }

    private fun setupPrinterUi() {
        val isSunmi = printerManager.printerType == PrinterManager.PrinterType.SUNMI_INTEGRATED
        binding.btnChoosePrinter.visibility =
            if (isSunmi) android.view.View.GONE else android.view.View.VISIBLE

        binding.tvPrinterStatus.text = if (isSunmi) {
            "Enprimant: SUNMI entegre — ap konekte..."
        } else {
            "Enprimant: Bluetooth eksitèn — ap konekte..."
        }
    }

    private fun connectPrinter() {
        printerManager.connect {
            runOnUiThread {
                val ready = printerManager.isReady()
                binding.tvPrinterStatus.text = if (ready) {
                    "Enprimant: konekte ✅"
                } else if (printerManager.printerType == PrinterManager.PrinterType.BLUETOOTH_EXTERNAL) {
                    "Enprimant: pa konekte.\n${printerManager.lastPrinterError ?: "chwazi youn pi ba a"}"
                } else {
                    "Enprimant: pa konekte — chwazi youn"
                }
            }
        }
    }

    private fun showBluetoothPrinterPicker() {
        val devices = printerManager.getPairedBluetoothPrinters()
        if (devices.isEmpty()) {
            Toast.makeText(
                this,
                "Pa gen aparèy Bluetooth pè. Al nan Paramèt > Bluetooth pou pè enprimant ou anvan.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chwazi enprimant Bluetooth")
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names)) { _, which ->
                val selected = devices[which]
                Toast.makeText(this, "Ap konekte ak ${selected.name}...", Toast.LENGTH_SHORT).show()
                Thread {
                    val success = printerManager.selectBluetoothPrinter(selected)
                    runOnUiThread {
                        binding.tvPrinterStatus.text = if (success) {
                            "Enprimant: konekte ✅ (${selected.name})"
                        } else {
                            "Enprimant: echèk koneksyon.\n${printerManager.lastPrinterError ?: "pa gen detay"}"
                        }
                    }
                }.start()
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
            val notGranted = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
            }
        }
    }

    override fun onDestroy() {
        printerManager.disconnect()
        super.onDestroy()
    }
}