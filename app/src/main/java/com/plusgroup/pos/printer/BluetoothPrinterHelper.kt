package com.plusgroup.pos.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.OutputStream
import java.util.UUID

/**
 * Enprimant tèmik EKSTÈN ki konekte pa Bluetooth, itilize pwotokòl ESC/POS
 * estanda. Sa mache ak PRESKE TOUT enprimant tèmik Bluetooth ki egziste
 * (san bezwen SDK manifaktirè espesyal) — e li mache sou NENPÒT aparèy
 * Android (telefòn òdinè kou POS klon ki pa gen SDK entegre pa yo).
 *
 * Itilizatè a dwe "PÈ" (pair) enprimant lan nan Paramèt Bluetooth telefòn
 * nan yon fwa anvan, apre sa app la ka konekte avè l otomatikman.
 */
class BluetoothPrinterHelper {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    /**
     * Lis tout aparèy Bluetooth ki DEJA pè ak telefòn/POS la.
     * Filtre pa non si w vle (egzanp: enprimant yo souvan gen "Printer",
     * "POS", "BT" nan non yo).
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        return try {
            disconnect()
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            this.socket = socket
            this.outputStream = socket.outputStream
            true
        } catch (e: Exception) {
            Log.e(TAG, "Echèk konekte ak enprimant Bluetooth la", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectByAddress(macAddress: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = adapter.getRemoteDevice(macAddress) ?: return false
        return connect(device)
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        socket = null
    }

    private fun write(bytes: ByteArray) {
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Echèk voye done bay enprimant la", e)
        }
    }

    fun printTestReceipt() {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine("PLUS GROUP"))
        write(boldOff())
        write(textLine("Tès enprimant Bluetooth — tout bon!"))
        write(feed(3))
        write(cutPaper())
    }

    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        write(ESC_INIT)
        write(alignCenter())
        write(boldOn())
        write(textLine(companyName))
        write(boldOff())
        write(textLine("BONNE CHANCE!"))
        write(feed(1))

        write(alignLeft())
        write(textLine("Tiraj: $drawName"))
        write(textLine("Tikè No: $ticketNumber"))
        write(textLine("Nimewo: $numbers"))
        write(textLine("Montan: $betAmount HTG"))
        write(feed(1))

        write(alignCenter())
        write(textLine(footerMessage))
        write(feed(4))
        write(cutPaper())
    }

    // --- Kòmand ESC/POS debaz ---

    private fun textLine(text: String): ByteArray =
        (text + "\n").toByteArray(Charsets.UTF_8)

    private fun alignLeft(): ByteArray = byteArrayOf(0x1B, 0x61, 0x00)
    private fun alignCenter(): ByteArray = byteArrayOf(0x1B, 0x61, 0x01)
    private fun boldOn(): ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
    private fun boldOff(): ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
    private fun feed(lines: Int): ByteArray = ByteArray(lines) { 0x0A }
    private fun cutPaper(): ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    companion object {
        private const val TAG = "BluetoothPrinterHelper"
        private const val ESC_INIT_BYTE: Byte = 0x1B
        private val ESC_INIT = byteArrayOf(ESC_INIT_BYTE, 0x40) // ESC @ = reinisyalize enprimant

        // UUID estanda Serial Port Profile (SPP) — itilize pa prèske tout
        // enprimant tèmik Bluetooth ki egziste.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
