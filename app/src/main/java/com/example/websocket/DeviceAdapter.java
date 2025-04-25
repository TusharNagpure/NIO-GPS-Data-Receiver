package com.example.websocket;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.TextView;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private final Context context;
    private final List<Device> devices;
    private final OnDeviceClickListener clickListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(Device device);
    }

    public DeviceAdapter(Context context, List<Device> devices, OnDeviceClickListener clickListener) {
        this.context = context;
        this.devices = devices;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_list_item, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = devices.get(position);
        holder.bind(device, clickListener, context, devices, this);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDeviceName;
        private final TextView tvDeviceDetails;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceDetails = itemView.findViewById(R.id.tv_device_details);
        }

        public void bind(Device device,
                         OnDeviceClickListener clickListener,
                         Context context,
                         List<Device> devices,
                         RecyclerView.Adapter<?> adapter) {

            tvDeviceName.setText(device.getName());
            tvDeviceDetails.setText(
                    String.format("Host: %s, Port: %d", device.getHost(), device.getPort())
            );

            // Handle normal click
            itemView.setOnClickListener(v -> clickListener.onDeviceClick(device));

            // Handle long press to show "Edit" or "Delete"
            itemView.setOnLongClickListener(v -> {
                CharSequence[] options = {"Edit Device", "Delete Device"};
                new AlertDialog.Builder(context)
                        .setTitle("Choose an action")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                // Edit
                                showEditDialog(context, device, devices, adapter);
                            } else if (which == 1) {
                                // Delete
                                deleteDevice(context, device, devices, adapter);
                            }
                        })
                        .show();
                return true;
            });
        }

        /**
         * Show an AlertDialog to edit device details,
         * including a Spinner of available IPs.
         */
        private void showEditDialog(Context context,
                                    Device device,
                                    List<Device> devices,
                                    RecyclerView.Adapter<?> adapter) {

            // Inflate the edit dialog layout
            View dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_edit_device, null);

            // Get references to Spinner & EditText fields
            Spinner spinnerIPs = dialogView.findViewById(R.id.spinner_ips_edit);
            EditText etName = dialogView.findViewById(R.id.et_edit_device_name);
            EditText etHost = dialogView.findViewById(R.id.et_edit_device_host);
            EditText etPort = dialogView.findViewById(R.id.et_edit_device_port);
            EditText etUsername = dialogView.findViewById(R.id.et_edit_device_username);
            EditText etPassword = dialogView.findViewById(R.id.et_edit_device_password);
            EditText etTargetDir = dialogView.findViewById(R.id.et_edit_device_target_directory);

            // Pre-fill fields with existing device data
            etName.setText(device.getName());
            etHost.setText(device.getHost());
            etPort.setText(String.valueOf(device.getPort()));
            etUsername.setText(device.getUsername());
            etPassword.setText(device.getPassword());
            etTargetDir.setText(device.getTargetDirectory());

            // Populate the Spinner with discovered IPs (runs in background)
            populateConnectedDevices(context, spinnerIPs, etHost);

            new AlertDialog.Builder(context)
                    .setTitle("Edit Device")
                    .setView(dialogView)
                    .setPositiveButton("Save", (dialog, which) -> {
                        // Gather the updated details
                        String updatedName = etName.getText().toString().trim();
                        String updatedHost = etHost.getText().toString().trim();
                        int updatedPort = Integer.parseInt(etPort.getText().toString().trim());
                        String updatedUsername = etUsername.getText().toString().trim();
                        String updatedPassword = etPassword.getText().toString().trim();
                        String updatedTargetDir = etTargetDir.getText().toString().trim();

                        // Remove old device from storage
                        DeviceStorage.removeDevice(context, device);

                        // Create an updated Device object
                        Device updatedDevice = new Device(
                                updatedName,
                                updatedHost,
                                updatedPort,
                                updatedUsername,
                                updatedPassword,
                                updatedTargetDir
                        );

                        // Add updated device to storage
                        DeviceStorage.addDevice(context, updatedDevice);

                        // Update in the current list and notify the adapter
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            devices.set(position, updatedDevice);
                            adapter.notifyItemChanged(position);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        /**
         * Reuses the scanning logic from AddDeviceActivity to discover
         * local IPs, then populates the Spinner. If the user picks an IP,
         * we set the Host editText to that IP.
         */
        private void populateConnectedDevices(Context context,
                                              Spinner spinnerIPs,
                                              EditText etHost) {
            new Thread(() -> {
                // 1) Scan for devices
                List<String> discoveredIPs = getConnectedDevicesFromARP();

                // If no devices found, add a single fallback
                if (discoveredIPs.isEmpty()) {
                    discoveredIPs.add("No devices connected");
                }

                // 2) Update the Spinner on the UI thread
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        // Create an ArrayAdapter for the discovered IPs
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                context,
                                android.R.layout.simple_spinner_item,
                                discoveredIPs
                        );
                        adapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item
                        );
                        spinnerIPs.setAdapter(adapter);

                        // Boolean array so we can modify it inside listeners
                        final boolean[] userClicked = {false};

                        // Detect when user actually touches the Spinner
                        spinnerIPs.setOnTouchListener((v, event) -> {
                            userClicked[0] = true;
                            return false; // Allow default behavior (show dropdown)
                        });

                        // Listen for item selections
                        spinnerIPs.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> parent,
                                                               View view,
                                                               int position,
                                                               long id) {
                                        // Only set the Host if user has actually touched the Spinner
                                        if (userClicked[0]) {
                                            String selectedIP = discoveredIPs.get(position);
                                            if (!selectedIP.equals("No devices connected")) {
                                                etHost.setText(selectedIP);
                                            }
                                        }
                                    }

                                    @Override
                                    public void onNothingSelected(AdapterView<?> parent) {
                                        // Do nothing
                                    }
                                }
                        );
                    });
                }
            }).start();
        }

        /**
         * Reads /proc/net/arp and tries pinging the subnet
         * to build a list of discovered IPs (same logic from AddDeviceActivity).
         */
        private List<String> getConnectedDevicesFromARP() {
            List<String> devicesList = new ArrayList<>();
            String subnet = getSubnet();
            if (subnet == null) {
                return devicesList;
            }

            // Attempt to ping the subnet
            for (int i = 1; i < 255; i++) {
                String ip = subnet + i;
                try {
                    InetAddress inetAddress = InetAddress.getByName(ip);
                    if (inetAddress.isReachable(100)) {
                        devicesList.add(ip);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Parse ARP table
            try (BufferedReader br = new BufferedReader(
                    new FileReader("/proc/net/arp"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        String ip = parts[0];
                        String mac = parts[3];
                        if (!mac.equals("00:00:00:00:00:00") &&
                                mac.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
                            // Avoid duplicates
                            if (!devicesList.contains(ip)) {
                                devicesList.add(ip);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return devicesList;
        }

        private String getSubnet() {
            try {
                for (Enumeration<NetworkInterface> interfaces =
                     NetworkInterface.getNetworkInterfaces();
                     interfaces.hasMoreElements();) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                         addresses.hasMoreElements();) {
                        InetAddress inetAddress = addresses.nextElement();
                        // IPv4 and not a loopback
                        if (!inetAddress.isLoopbackAddress()
                                && inetAddress instanceof java.net.Inet4Address) {
                            String ip = inetAddress.getHostAddress();
                            return ip.substring(0, ip.lastIndexOf(".") + 1); // e.g. 192.168.0.
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Original delete logic
         */
        private void deleteDevice(Context context,
                                  Device device,
                                  List<Device> devices,
                                  RecyclerView.Adapter<?> adapter) {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Device")
                    .setMessage("Are you sure you want to delete this device?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        DeviceStorage.removeDevice(context, device);
                        int adapterPosition = getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            devices.remove(adapterPosition);
                            adapter.notifyItemRemoved(adapterPosition);
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }
}
