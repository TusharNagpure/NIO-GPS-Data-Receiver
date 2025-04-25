package com.example.websocket;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

public class DeviceSelectionActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private List<Device> devices = new ArrayList<>();
    private DeviceAdapter adapter;

    // 1) Create a launcher for the AddDeviceActivity
    private final ActivityResultLauncher<Intent> addDeviceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Reload devices once a new device is successfully added
                    loadDevices();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_selection);

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));

        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(getResources().getColor(android.R.color.white));
        }


        drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        RecyclerView deviceRecyclerView = findViewById(R.id.deviceRecyclerView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        deviceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(this, devices, this::onDeviceClick);
        deviceRecyclerView.setAdapter(adapter);

        loadDevices();

        // 2) Use the ActivityResultLauncher to start AddDeviceActivity
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(DeviceSelectionActivity.this, AddDeviceActivity.class);
            addDeviceLauncher.launch(intent);
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_home) {
                Toast.makeText(this, "Home clicked", Toast.LENGTH_SHORT).show();
            }else if (id == R.id.menu_about) {
                // Show About Dialog
                showAboutDialog();
            }

            drawerLayout.closeDrawer(GravityCompat.START); // Close the drawer after selection
            return true;
        });


        // Drawer toggle setup
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();


    }

    private void showAboutDialog() {
        // Create a custom dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        // Inflate the custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
        builder.setView(dialogView);

        // Optional: Close button for the dialog
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        // Show the dialog
        builder.create().show();
    }


    private void onDeviceClick(Device device) {
        Intent intent = new Intent(DeviceSelectionActivity.this, FileBrowserActivity.class);
        intent.putExtra("host", device.getHost());
        intent.putExtra("port", device.getPort());
        intent.putExtra("username", device.getUsername());
        intent.putExtra("password", device.getPassword());
        intent.putExtra("TargetDirectory",device.getTargetDirectory());
        startActivity(intent);
    }

    private void loadDevices() {
        devices.clear();
        devices.addAll(DeviceStorage.getDevices(this));
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If you want to auto-refresh each time the activity resumes:
        // loadDevices();
    }
}
