package com.example.websocket;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileBrowserActivity extends AppCompatActivity {
    private static final String TAG = "SFTP";
    private Session session;
    private ChannelSftp channelSftp;
    private String currentPath;
    private FileAdapter adapter;
    private boolean isSelectMode = false;
    private List<ChannelSftp.LsEntry> originalFiles = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        TextView tvPath = findViewById(R.id.tv_path);
        RecyclerView rvFiles = findViewById(R.id.rv_files);
        ImageView btnRefresh = findViewById(R.id.btn_refresh);
        ImageView btnFilter = findViewById(R.id.btn_filter);
        ImageView btnSelect = findViewById(R.id.btn_select);
        ImageView btnDownload = findViewById(R.id.btn_download);
        ImageView btnGPS = findViewById(R.id.btn_gps_location);

        // Divider
        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                rvFiles.getContext(), DividerItemDecoration.VERTICAL);
        rvFiles.addItemDecoration(dividerItemDecoration);

        // Get SFTP connection info
        String host = getIntent().getStringExtra("host");
        int port = getIntent().getIntExtra("port", 22);
        String username = getIntent().getStringExtra("username");
        String password = getIntent().getStringExtra("password");
        String targetDirectory = getIntent().getStringExtra("TargetDirectory");
        currentPath = targetDirectory;

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Connect in background thread
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, host, port);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Load ONLY today's files
                loadFiles(rvFiles, tvPath);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }).start();

        // Listeners
        btnRefresh.setOnClickListener(v -> new Thread(() -> loadFiles(rvFiles, tvPath)).start());
        //btnFilter.setOnClickListener(v -> showFilterDialog(rvFiles));
        btnSelect.setOnClickListener(v -> {
            if (adapter != null) {
                isSelectMode = !isSelectMode;
                adapter.toggleSelectAll(isSelectMode);
                btnSelect.setImageResource(isSelectMode
                        ? R.drawable.ic_deselect_foreground
                        : R.drawable.ic_select_foreground);
                String message = isSelectMode ? "All files selected" : "All files deselected";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });


        btnFilter.setOnClickListener(v -> showFilterDialog(rvFiles));
        btnDownload.setOnClickListener(v -> {
            if (adapter != null) {
                List<String> selectedFiles = adapter.getSelectedFiles();
                if (selectedFiles.isEmpty()) {
                    Toast.makeText(this, "No files selected for download", Toast.LENGTH_SHORT).show();
                } else {
                    downloadSelectedFiles(selectedFiles);
                }
            }
        });
        btnGPS.setOnClickListener(v -> {
            deleteGpsLogsData();
            Intent gpsIntent = new Intent(FileBrowserActivity.this, GPSDataActivity.class);
            startActivity(gpsIntent);
            gpsIntent.putExtra("currentPath", currentPath);
            gpsIntent.putExtra("host", host);
            gpsIntent.putExtra("port", port);
            gpsIntent.putExtra("username", username);
            gpsIntent.putExtra("password", password);
            startActivity(gpsIntent);
        });
    }

    private void deleteGpsLogsData() {
        File gpsLogsDataDir = new File("/storage/emulated/0/documents/gpslogsdata");
        if (gpsLogsDataDir.exists() && gpsLogsDataDir.isDirectory()) {
            File[] files = gpsLogsDataDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete(); // Delete each file
                }
            }
            gpsLogsDataDir.delete(); // Delete the directory
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Load only files for today's date from the remote directory.
     */
    private void loadFiles(RecyclerView rvFiles, TextView tvPath) {
        try {
            // 1) Fetch only the current date's files:
            List<ChannelSftp.LsEntry> files = listFilesByDate(currentPath);

            // Keep a copy for local filtering if needed
            originalFiles = new ArrayList<>(files);

            // 2) Update UI
            runOnUiThread(() -> {
                tvPath.setText("Path: " + currentPath);

                adapter = new FileAdapter(files, this, new FileAdapter.FileClickListener() {
                    @Override
                    public void onDirectoryClick(String directoryPath) {
                        // If you still need to navigate into subdirectories
                        // you can repeat logic for that subdirectory
                        currentPath = currentPath + "/" + directoryPath;
                        new Thread(() -> loadFiles(rvFiles, tvPath)).start();
                    }

                    @Override
                    public void onFileClick(String fileName) {
                        downloadFile(fileName);
                    }
                });

                rvFiles.setAdapter(adapter);

                // Scroll to the bottom if you want
                if (adapter.getItemCount() > 0) {
                    rvFiles.scrollToPosition(adapter.getItemCount() - 1);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(this, "Error loading files: " + e.getMessage(),
                            Toast.LENGTH_LONG).show()
            );
        }
    }

    private void showFilterDialog(RecyclerView rvFiles) {
        String[] filterOptions = {"Filter by Name", "Filter by Date"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter Files");
        builder.setItems(filterOptions, (dialog, which) -> {
            switch (which) {
                case 0: // Filter by Name
                    filterByName(rvFiles);
                    break;
                case 1: // Filter by Date
                    filterByDate(rvFiles);
                    break;
            }
        });
        builder.show();
    }

    private void filterByName(RecyclerView rvFiles) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter File Name");
        final View customLayout = getLayoutInflater().inflate(R.layout.filter_input, null);
        builder.setView(customLayout);
        builder.setPositiveButton("Filter", (dialog, which) -> {
            TextView input = customLayout.findViewById(R.id.filter_input);
            String nameFilter = input.getText().toString().trim();
            Log.d("FilterDebug", "Filter value: " + nameFilter);

            // Ensure originalFiles is not null
            if (originalFiles == null) {
                Toast.makeText(this, "File list not initialized", Toast.LENGTH_SHORT).show();
                return;
            }

            // Debug: Log all original files
            for (ChannelSftp.LsEntry file : originalFiles) {
                Log.d("FilterDebug", "Original file name: " + file.getFilename());
            }

            // Filter the files by name
            List<ChannelSftp.LsEntry> filteredFiles = new ArrayList<>();
            for (ChannelSftp.LsEntry file : originalFiles) {
                if (file.getFilename().toLowerCase().contains(nameFilter.toLowerCase())) {
                    filteredFiles.add(file);
                    Log.d("FilterDebug", "Matching file: " + file.getFilename());
                }
            }

            // Update the file list or show a message if no files are found
            if (!filteredFiles.isEmpty()) {
                updateFileList(rvFiles, filteredFiles);
            } else {
                Toast.makeText(this, "No matching files found", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void filterByDate(RecyclerView rvFiles) {
        // Get today's date for default in DatePicker
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH); // Month is 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // Open DatePickerDialog with today's date as the default
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            // Format the selected date to "YYYYMMDD" (for file filtering)
            String selectedDate = String.format("%04d%02d%02d", selectedYear, selectedMonth + 1, selectedDay);

            // Define the pattern for the selected date
            String datePattern = "*" + selectedDate + "*.ubx"; // Example: *20250102*.ubx

            // Fetch files matching the selected date
            new Thread(() -> {
                try {
                    // List files matching the date pattern from the current directory
                    List<ChannelSftp.LsEntry> filteredFiles = listFilesByPattern(currentPath, datePattern);

                    // Update the file list on the UI thread
                    runOnUiThread(() -> {
                        if (!filteredFiles.isEmpty()) {
                            updateFileList(rvFiles, filteredFiles);
                            Toast.makeText(this, "Files from " + selectedDate + " displayed", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "No files found for the selected date", Toast.LENGTH_SHORT).show();
                            // Clear the file list if no matches
                            updateFileList(rvFiles, new ArrayList<>());
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(this, "Error filtering files: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();
        }, year, month, day);

        // Show the DatePickerDialog
        datePickerDialog.show();
    }


    private void updateFileList(RecyclerView rvFiles, List<ChannelSftp.LsEntry> files) {
        adapter.updateFiles(files);
        rvFiles.scrollToPosition(adapter.getItemCount() - 1);
    }


    /**
     * Return a list of files that match the "current date" pattern, e.g. *20250103*.ubx
     */
    private List<ChannelSftp.LsEntry> listFilesByDate(String path) throws SftpException {
        // 1) Build pattern for today's date in yyyyMMdd format:
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        // Example: "*_20250103-*.ubx" or just "*20250103*.ubx"
        String pattern = path + "/*" + today + "*.ubx";

        // 2) Use the pattern in channelSftp.ls()
        List<ChannelSftp.LsEntry> files;
        files = (List<ChannelSftp.LsEntry>) channelSftp.ls(pattern);

        // 3) Filter out "." and ".." if they appear
        List<ChannelSftp.LsEntry> validFiles = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : files) {
            String fileName = entry.getFilename();
            if (!".".equals(fileName) && !"..".equals(fileName)) {
                validFiles.add(entry);
            }
        }

        return validFiles;
    }

    private List<ChannelSftp.LsEntry> listFilesByPattern(String path, String pattern) throws SftpException {
        // Build the complete pattern for the selected date
        String fullPattern = path + "/" + pattern;

        // Fetch files matching the pattern
        List<ChannelSftp.LsEntry> files = (List<ChannelSftp.LsEntry>) channelSftp.ls(fullPattern);

        // Filter out "." and ".." entries
        List<ChannelSftp.LsEntry> validFiles = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : files) {
            String fileName = entry.getFilename();
            if (!fileName.equals(".") && !fileName.equals("..")) {
                validFiles.add(entry);
            }
        }
        return validFiles;
    }


    private void downloadFile(String fileName) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Downloading file...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                String remoteFile = currentPath + "/" + fileName;

                File documentsDir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "GPSDataReceiver"
                );
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs();
                }
                String localFile = new File(documentsDir, fileName).getAbsolutePath();

                // Download
                FileOutputStream fos = new FileOutputStream(localFile);
                channelSftp.get(remoteFile, fos);
                fos.close();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Downloaded to: " + localFile, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error downloading: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void downloadSelectedFiles(List<String> selectedFiles) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Downloading files...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                File documentsDir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "GPSDataReceiver"
                );
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs();
                }

                // Download each file
                for (String fileName : selectedFiles) {
                    String remoteFile = currentPath + "/" + fileName;
                    String localFile = new File(documentsDir, fileName).getAbsolutePath();

                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        channelSftp.get(remoteFile, fos);
                    }
                }

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Files downloaded successfully", Toast.LENGTH_SHORT).show();
                    adapter.resetSelection();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error downloading files: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.areCheckboxesVisible()) {
            adapter.resetSelection();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
