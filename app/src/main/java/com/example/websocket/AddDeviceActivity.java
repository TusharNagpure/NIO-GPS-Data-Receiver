package com.example.websocket;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class AddDeviceActivity extends AppCompatActivity {

    private Spinner spinnerIPs;
    private EditText etDir, etName, etHost, etPort, etUsername, etPassword;
    private String selectedIP;
    private boolean isUserInteracting = false; // Flag to track user interaction

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        spinnerIPs = findViewById(R.id.spinner_ips);
        etDir = findViewById(R.id.et_Dir);
        etName = findViewById(R.id.et_name);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        Button btnSave = findViewById(R.id.btn_save);

        // Populate Spinner with Connected Devices
        populateConnectedDevices();

        // Set listener to track when user clicks on the Spinner
        spinnerIPs.setOnTouchListener((v, event) -> {
            isUserInteracting = true; // User is now interacting with the Spinner
            return false; // Allow default behavior
        });

        // Update etHost only when user selects an item manually
        spinnerIPs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUserInteracting) {
                    String selectedIP = (String) parent.getItemAtPosition(position);
                    if (!"No devices connected".equals(selectedIP)) {
                        etHost.setText(selectedIP);
                    }
                    isUserInteracting = false; // Reset the flag after setting the text
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });


        // 2) Save the device based on what's in etHost (typed or set from spinner)
        btnSave.setOnClickListener(v -> {
            String host = etHost.getText().toString().trim();
            String port = etPort.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String name = etName.getText().toString().trim();
            String targetDirectory = etDir.getText().toString().trim();

            // Basic validation
            if (host.isEmpty() || port.isEmpty() || username.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (targetDirectory.isEmpty()) {
                targetDirectory = "/mnt/c/";
            }

            // 3) Create and save the device
            Device device = new Device(name, host, Integer.parseInt(port), username, password, targetDirectory);
            DeviceStorage.addDevice(this, device);

            Toast.makeText(this, "Device added successfully!", Toast.LENGTH_SHORT).show();

            // Notify parent Activity and close
            setResult(RESULT_OK);
            finish();
        });
    }

    private void populateConnectedDevices() {
        new Thread(() -> {
            List<String> devices = getConnectedDevicesFromARP();
            if (devices.isEmpty()) {
                devices.add("No devices connected");
            }

            // Update the Spinner on the main thread
            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        devices
                ) {
                    @Override
                    public boolean isEnabled(int position) {
                        return !getItem(position).equals("No devices connected");
                    }
                };
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerIPs.setAdapter(adapter);
            });
        }).start();
    }

    /**
     * Example code to discover IPs on your local subnet.
     * This tries to 'ping' them to populate the ARP table,
     * then reads /proc/net/arp for additional discovered addresses.
     */
    private List<String> getConnectedDevicesFromARP() {
        List<String> devices = new ArrayList<>();
        String subnet = getSubnet();
        if (subnet == null) {
            return devices;
        }

        // Attempt to ping the subnet to populate the ARP table
        for (int i = 1; i < 255; i++) {
            String ip = subnet + i;
            try {
                InetAddress inetAddress = InetAddress.getByName(ip);
                if (inetAddress.isReachable(100)) {
                    devices.add(ip);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Parse ARP table
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4) {
                    String ip = parts[0];
                    String mac = parts[3];
                    if (!mac.equals("00:00:00:00:00:00") &&
                            mac.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
                        devices.add(ip);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return devices;
    }

    /**
     * Attempts to find the local subnet by enumerating
     * network interfaces and returning the first non-loopback IPv4 address.
     */
    private String getSubnet() {
        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                 interfaces.hasMoreElements();) {
                NetworkInterface networkInterface = interfaces.nextElement();
                for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                     addresses.hasMoreElements();) {
                    InetAddress inetAddress = addresses.nextElement();
                    // IPv4 and not a loopback
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        // Example: 192.168.0.???
                        return ip.substring(0, ip.lastIndexOf(".") + 1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
