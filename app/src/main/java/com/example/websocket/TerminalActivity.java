package com.example.websocket;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class TerminalActivity extends AppCompatActivity {

    private static final String TAG = "TerminalActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.websocket.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbSerialPort serialPort;

    // UI Elements
    private TextView incomingDataText;
    private ScrollView scrollView;
    private Button pauseResumeButton;   // or ImageView if you prefer
    private Button clearButton;         // or ImageView if you prefer
    private ImageView saveButton;       // The "save" icon
    private boolean isStreaming = false; // Data streaming toggle

    // For saving .ubx
    private boolean isSaving = false;
    private FileOutputStream fos; // Where we write UBX data

    // ByteQueue for partial UBX reads
    private final ByteQueue ubxByteQueue = new ByteQueue();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal); // Make sure you have such a layout

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Find your UI elements by ID (adjust IDs to match your layout)
        incomingDataText    = findViewById(R.id.incomingDataText);
        scrollView          = findViewById(R.id.scrollView);
        pauseResumeButton   = findViewById(R.id.pauseResumeButton);
        clearButton         = findViewById(R.id.ClearButton);
        saveButton          = findViewById(R.id.saveButton);

        // Pause/Resume
        pauseResumeButton.setOnClickListener(v -> {
            isStreaming = !isStreaming;
            pauseResumeButton.setText(isStreaming ? "Pause" : "Resume");
            if (isStreaming) {
                startDataStreaming();
            } else {
                stopDataStreaming();
            }
        });

        // Clear text
        clearButton.setOnClickListener(v -> incomingDataText.setText(""));

        // Save button (toggles saving .ubx file)
        saveButton.setOnClickListener(v -> {
            if (!isSaving) {
                startSavingUbxFile();
            } else {
                stopSavingUbxFile();
            }
        });

        // Register for USB attach/detach + permission
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        checkPermissions();
        checkConnectedDevices();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 112);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 112) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Needs storage permission to save data.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Check connected USB devices. For simplicity, pick the first recognized driver.
     */
    private void checkConnectedDevices() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No USB devices found");
            return;
        }

        Log.d(TAG, "Found " + availableDrivers.size() + " USB device(s).");
        // Pick the first device
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // Request permission
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT
        );
        usbManager.requestPermission(device, permissionIntent);
    }

    /**
     * BroadcastReceiver to handle USB permission, device attach/detach.
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(TAG, "USB permission granted for device: " + device.getDeviceName());
                        openSerialPort(device);
                    } else {
                        Log.d(TAG, "Permission denied for device or device is null");
                        Toast.makeText(context, "Permission denied for device", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB device attached");
                checkConnectedDevices();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB device detached");
                if (serialPort != null) {
                    try {
                        serialPort.close();
                        Log.d(TAG, "Serial port closed");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing serial port", e);
                    }
                }
                serialPort = null;
            }
        }
    };

    /**
     * Attempt to open the serial port (using the driver that matched the device).
     */
    private void openSerialPort(UsbDevice device) {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        for (UsbSerialDriver driver : availableDrivers) {
            if (driver.getDevice().equals(device)) {
                try {
                    serialPort = driver.getPorts().get(0);
                    serialPort.open(usbManager.openDevice(driver.getDevice()));
                    // Adjust baud, data bits, etc. as needed for your device
                    serialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    serialPort.setDTR(true);
                    serialPort.setRTS(true);

                    Log.d(TAG, "Serial port opened at 9600 baud");
                    if (isStreaming) {
                        startDataStreaming();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error opening serial port: " + e.getMessage(), e);
                    try {
                        serialPort.close();
                    } catch (IOException e2) {
                        Log.e(TAG, "Error closing serial port", e2);
                    }
                    serialPort = null;
                }
                break;
            }
        }
    }

    /**
     * Start reading data in a background thread.
     */
    private void startDataStreaming() {
        if (serialPort != null) {
            new Thread(new UbxReader()).start();
            Log.d(TAG, "Data streaming started");
        } else {
            Log.d(TAG, "Serial port is null or not open");
        }
    }

    private void stopDataStreaming() {
        isStreaming = false;
        Log.d(TAG, "Data streaming stopped");
    }

    // -------------- Save / Logging .ubx  --------------
    private void startSavingUbxFile() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "ubx_" + timestamp + ".ubx";

        File docsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ubx_data");
        if (!docsDir.exists()) {
            docsDir.mkdirs();
        }

        File outFile = new File(docsDir, fileName);
        try {
            fos = new FileOutputStream(outFile);
            isSaving = true;
            Toast.makeText(this, "Saving to " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "Started saving UBX to " + outFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to open file: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopSavingUbxFile() {
        if (fos != null) {
            try {
                fos.close();
                fos = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        isSaving = false;
        Toast.makeText(this, "Stopped saving UBX", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Stopped saving UBX");
    }
    // ---------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);

        if (serialPort != null) {
            try {
                serialPort.close();
                Log.d(TAG, "Serial port closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
        }

        // If still saving, close the file
        if (fos != null) {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Background thread that reads raw bytes from the serial port and feeds them to parseUbxQueue().
     */
    private class UbxReader implements Runnable {
        @Override
        public void run() {
            byte[] readBuffer = new byte[1024];
            while (isStreaming && serialPort != null) {
                try {
                    int numBytes = serialPort.read(readBuffer, 500);
                    if (numBytes > 0) {
                        ubxByteQueue.enqueue(readBuffer, 0, numBytes);
                        parseUbxQueue();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading serial data", e);
                    break;
                }
            }
            Log.d(TAG, "UbxReader thread exit");
        }
    }

    private void parseUbxQueue() {
        // UBX min size = 8 bytes (2 sync + 4 header + 2 cksum)
        while (ubxByteQueue.size() >= 8) {
            int syncIndex = findSyncBytes();
            if (syncIndex < 0) {
                // No sync, discard 1 byte
                ubxByteQueue.discard(1);
                return;
            }

            if (syncIndex > 0) {
                ubxByteQueue.discard(syncIndex);
            }

            // Check again that we have enough data for at least a UBX header
            if (ubxByteQueue.size() < 8) {
                return;
            }

            byte msgClass = ubxByteQueue.peek(2);
            byte msgId    = ubxByteQueue.peek(3);
            int lengthLSB = ubxByteQueue.peek(4) & 0xFF;
            int lengthMSB = ubxByteQueue.peek(5) & 0xFF;
            int payloadLen = (lengthMSB << 8) | lengthLSB;

            int totalSize = 6 + payloadLen + 2; // header + payload + 2-checksum
            if (ubxByteQueue.size() < totalSize) {
                return; // not enough yet
            }

            // Extract
            byte[] packet = new byte[totalSize];
            ubxByteQueue.peek(packet, 0, totalSize);

            // Checksum
            if (!isUbxChecksumValid(packet)) {
                ubxByteQueue.discard(1);
                continue;
            }

            // Valid packet, remove it from queue
            ubxByteQueue.discard(totalSize);

            // Filter to relevant messages:
            int c = msgClass & 0xFF;
            int i = msgId & 0xFF;
            boolean isNavPvt   = (c == 0x01 && i == 0x07);
            boolean isRxmSfrbx = (c == 0x02 && i == 0x13);
            boolean isRxmMeasx = (c == 0x02 && i == 0x14);
            boolean isRxmRawx  = (c == 0x02 && i == 0x15);

            if (isNavPvt || isRxmSfrbx || isRxmMeasx || isRxmRawx) {
                handleUbxPacket(packet);
            }
        }
    }

    private int findSyncBytes() {
        for (int idx = 0; idx < ubxByteQueue.size() - 1; idx++) {
            int b1 = ubxByteQueue.peek(idx) & 0xFF;
            int b2 = ubxByteQueue.peek(idx + 1) & 0xFF;
            if (b1 == 0xB5 && b2 == 0x62) {
                return idx;
            }
        }
        return -1;
    }

    private boolean isUbxChecksumValid(byte[] packet) {
        // packet[0] = 0xB5, packet[1] = 0x62
        int lengthLSB = packet[4] & 0xFF;
        int lengthMSB = packet[5] & 0xFF;
        int payloadLen = (lengthMSB << 8) | lengthLSB;
        int payloadStart = 6;
        int payloadEnd   = payloadStart + payloadLen; // exclusive

        if (payloadEnd + 2 > packet.length) {
            return false;
        }

        byte ckA = 0;
        byte ckB = 0;
        for (int i = 2; i < payloadEnd; i++) {
            ckA += packet[i];
            ckB += ckA;
        }
        return (ckA == packet[payloadEnd]) && (ckB == packet[payloadEnd + 1]);
    }

    /**
     * Display UBX packet in hex and optionally save it to the file if isSaving == true.
     */
    private void handleUbxPacket(byte[] packet) {
        final String hex = bytesToHex(packet);
        runOnUiThread(() -> {
            incomingDataText.append(hex + "\n");
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });

        if (isSaving && fos != null) {
            try {
                fos.write(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Simple ByteQueue for partial UBX packet parsing.
     */
    private static class ByteQueue {
        private static final int INITIAL_CAPACITY = 4096;
        private byte[] buffer = new byte[INITIAL_CAPACITY];
        private int head = 0;
        private int tail = 0;

        public synchronized void enqueue(byte[] data, int offset, int length) {
            ensureCapacity(size() + length);
            for (int i = 0; i < length; i++) {
                buffer[tail] = data[offset + i];
                tail = (tail + 1) % buffer.length;
            }
        }

        private void ensureCapacity(int desiredCapacity) {
            if (desiredCapacity <= buffer.length) return;
            int newCapacity = buffer.length;
            while (newCapacity < desiredCapacity) {
                newCapacity *= 2;
            }
            byte[] newBuffer = new byte[newCapacity];
            int currentSize = size();
            for (int i = 0; i < currentSize; i++) {
                newBuffer[i] = buffer[(head + i) % buffer.length];
            }
            buffer = newBuffer;
            head = 0;
            tail = currentSize;
        }

        public synchronized int size() {
            if (tail >= head) {
                return tail - head;
            } else {
                return buffer.length - head + tail;
            }
        }

        public synchronized byte peek(int index) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException("Index out of range: " + index);
            }
            return buffer[(head + index) % buffer.length];
        }

        public synchronized void peek(byte[] dest, int offset, int length) {
            if (length > size()) {
                throw new IndexOutOfBoundsException("Not enough data to peek.");
            }
            for (int i = 0; i < length; i++) {
                dest[offset + i] = buffer[(head + i) % buffer.length];
            }
        }

        public synchronized void discard(int n) {
            if (n > size()) {
                throw new IndexOutOfBoundsException("Discarding more data than present.");
            }
            head = (head + n) % buffer.length;
        }
    }
}