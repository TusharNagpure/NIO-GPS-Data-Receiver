package com.example.websocket;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPSDataActivity extends AppCompatActivity {
    private ChannelSftp channelSftp;
    private String lastDownloadedFile = null; // To track the last downloaded file
    private Thread monitoringThread; // Thread reference
    private volatile boolean isMonitoring = true; // Flag to control the thread
    private TextView gpsUtcTime, latitude, longitude, heightEllipsoid, heightMsl, fixType, horizontalAccuracy, verticalAccuracy;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps_data);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Initialize the TextViews
        gpsUtcTime = findViewById(R.id.gps_utc_time);
        latitude = findViewById(R.id.latitude);
        longitude = findViewById(R.id.longitude);
        heightEllipsoid = findViewById(R.id.height_ellipsoid);
        heightMsl = findViewById(R.id.height_msl);
        fixType = findViewById(R.id.fix_type);
        horizontalAccuracy = findViewById(R.id.horizontal_accuracy);
        verticalAccuracy = findViewById(R.id.vertical_accuracy);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Enables back button
        }

        // Retrieve SFTP credentials
        String host = getIntent().getStringExtra("host");
        int port = getIntent().getIntExtra("port", 22);
        String username = getIntent().getStringExtra("username");
        String password = getIntent().getStringExtra("password");
        String targetDirectory = getIntent().getStringExtra("currentPath");

        if (host == null || host.isEmpty()) {
            finish(); // Close the activity if the host is null
            return;
        }
        // Initialize the SFTP connection
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, host, port);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();



                // Start monitoring and downloading the latest file
                monitorForLatestFiles(targetDirectory);

                parseUBXFilesInDirectory("/storage/emulated/0/documents/gpslogsdata");
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error connecting to SFTP: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateGPSData(NavPvtData data) {
        runOnUiThread(() -> {
            gpsUtcTime.setText(data.time);
            latitude.setText(String.format("%.6f°", data.latitude));
            longitude.setText(String.format("%.6f°", data.longitude));
            heightEllipsoid.setText(String.format("%.2f m", data.heightEllipsoid));
            heightMsl.setText(String.format("%.2f m", data.heightMsl));
            horizontalAccuracy.setText(String.format("%.3f m", data.horizontalAccuracy));
            verticalAccuracy.setText(String.format("%.3f m", data.verticalAccuracy));
            fixType.setText(data.fixType);
        });
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // Navigate back
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void monitorForLatestFiles(String currentPath) {
        monitoringThread = new Thread(() -> {
            try {
                String currentTimestamp = getCurrentTimestamp();
                HashSet<String> downloadedFiles = new HashSet<>();

                while (isMonitoring) { // Check the monitoring flag
                    Log.d("SFTP", "Monitoring directory: " + currentPath); // Log directory being monitored
                    Vector<ChannelSftp.LsEntry> files = (Vector<ChannelSftp.LsEntry>) channelSftp.ls(currentPath);

                    if (files != null && !files.isEmpty()) {
                        for (ChannelSftp.LsEntry file : files) {
                            String fileName = file.getFilename();
                            String fileTimestamp = extractTimestamp(fileName);

                            if (fileTimestamp != null
                                    && fileTimestamp.compareTo(currentTimestamp) >= 0
                                    && !downloadedFiles.contains(fileName)) {

                                String localFilePath = downloadFile(currentPath, fileName);
                                downloadedFiles.add(fileName);

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Downloaded: " + fileName, Toast.LENGTH_SHORT).show();
                                });
                                parseUBXFilesInDirectory("/storage/emulated/0/documents/gpslogsdata");

                            }
                        }
                    }

                    Thread.sleep(10000); // Wait for 10 second before checking again
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Reset the interrupt status
                runOnUiThread(() -> Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error monitoring files: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        monitoringThread.start(); // Start the thread
    }


    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Set the timezone to UTC
        return sdf.format(new Date());
    }



    // Method to extract the timestamp from the filename
    private String extractTimestamp(String fileName) {
        // General pattern to match any timestamp in the format YYYYMMDD-HHMMSS
        String pattern = "(\\d{8}-\\d{6})";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1); // Extracted timestamp
        }
        return ""; // Return empty string if no timestamp is found
    }


    private List<ChannelSftp.LsEntry> listFiles(String path) throws SftpException {
        List<ChannelSftp.LsEntry> files = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : (List<ChannelSftp.LsEntry>) channelSftp.ls(path)) {
            if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                files.add(entry);
            }
        }
        // Sort files by modification time (ascending order)
        files.sort((file1, file2) -> Long.compare(file1.getAttrs().getMTime(), file2.getAttrs().getMTime()));
        return files;
    }

    private String downloadFile(String currentPath, String fileName) throws Exception {
        String remoteFile = currentPath + "/" + fileName;

        File documentsDir = new File("/storage/emulated/0/documents/gpslogsdata");
        if (!documentsDir.exists()) {
            documentsDir.mkdirs();
        }

        String localFilePath = new File(documentsDir, fileName).getAbsolutePath();

        try (FileOutputStream fos = new FileOutputStream(localFilePath)) {
            channelSftp.get(remoteFile, fos);
        }

        return localFilePath;
    }

    private void parseUBXFile(String localFilePath) {
        try {
            UBXParser parser = new UBXParser();
            List<NavPvtData> navPvtDataList = parser.parseFileAndExtractFifteenNAVPVT(localFilePath);

            // Update the first NAV-PVT message in the table
            if (!navPvtDataList.isEmpty()) {
                NavPvtData data = navPvtDataList.get(0);
                updateGPSData(data);
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error parsing UBX file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }



    // Set to track parsed files
    private final HashSet<String> parsedFiles = new HashSet<>();

    private void parseUBXFilesInDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            runOnUiThread(() -> Toast.makeText(this, "Directory not found: " + directoryPath, Toast.LENGTH_SHORT).show());
            return;
        }

        File[] ubxFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".ubx"));
        if (ubxFiles == null || ubxFiles.length == 0) {
            runOnUiThread(() -> Toast.makeText(this, "No UBX files found in directory: " + directoryPath, Toast.LENGTH_SHORT).show());
            return;
        }

        for (File file : ubxFiles) {
            // Check if the file has already been parsed
            if (parsedFiles.contains(file.getAbsolutePath())) {
                continue; // Skip already parsed files
            }

            try {
                UBXParser parser = new UBXParser();
                List<NavPvtData> navPvtDataList = parser.parseFileAndExtractFifteenNAVPVT(file.getAbsolutePath());

                // Update UI for each NAV-PVT data sequentially
                for (NavPvtData data : navPvtDataList) {
                    updateGPSData(data);
                    Thread.sleep(2000); // Optional delay for observing updates
                }

                // Mark the file as parsed
                parsedFiles.add(file.getAbsolutePath());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error parsing file: " + file.getName() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }



    @Override
    public void onBackPressed() {
        isMonitoring = false; // Stop monitoring
        if (monitoringThread != null && monitoringThread.isAlive()) {
            monitoringThread.interrupt(); // Interrupt the thread
        }

        super.onBackPressed(); // Handle the back press
    }
}
