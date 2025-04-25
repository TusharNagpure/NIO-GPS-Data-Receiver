package com.example.websocket;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DeviceStorage {
    private static final String PREFS_NAME = "DevicePrefs";
    private static final String DEVICES_KEY = "Devices";

    // Save a new device
    public static void addDevice(Context context, Device device) {
        List<Device> devices = getDevices(context);
        devices.add(device);
        saveDevices(context, devices); // Use the saveDevices method
    }

    // Retrieve the list of devices
    public static List<Device> getDevices(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(DEVICES_KEY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Gson gson = new Gson();
        Type type = new TypeToken<List<Device>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // Save the list of devices
    private static void saveDevices(Context context, List<Device> devices) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(devices);
        editor.putString(DEVICES_KEY, json);
        editor.apply();
    }

    // Remove a device from storage
    public static void removeDevice(Context context, Device device) {
        List<Device> devices = getDevices(context);

        // Find and remove the matching device
        for (int i = 0; i < devices.size(); i++) {
            Device stored = devices.get(i);
            if (stored.getName().equals(device.getName()) &&
                    stored.getHost().equals(device.getHost()) &&
                    stored.getPort() == device.getPort()) {
                devices.remove(i);
                break;
            }
        }
        saveDevices(context, devices); // Save the updated list
    }
}
