package com.example.websocket;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jcraft.jsch.ChannelSftp;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
    private final List<ChannelSftp.LsEntry> files;
    private final Context context;
    private final FileClickListener clickListener;
    private boolean areCheckboxesVisible = false; // Track visibility of checkboxes
    private boolean[] selectedStates; // Track selection states of files
    private final List<String> selectedFiles = new ArrayList<>();


    public interface FileClickListener {
        void onDirectoryClick(String directoryPath);
        void onFileClick(String fileName);
    }

    public FileAdapter(List<ChannelSftp.LsEntry> files, Context context, FileClickListener clickListener) {
        this.files = files;
        this.context = context;
        this.clickListener = clickListener;
        this.selectedStates = new boolean[files.size()]; // Initialize selection states
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        ChannelSftp.LsEntry entry = files.get(position);
        String fileName = entry.getFilename();

        holder.fileName.setText(fileName);

        // Ensure the checkbox visibility is set correctly
        holder.checkBox.setVisibility(areCheckboxesVisible ? View.VISIBLE : View.INVISIBLE);

        // Set the checkbox state from selectedStates
        holder.checkBox.setChecked(selectedStates[position]);

        // Handle checkbox state change
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectedStates[position] = isChecked;
            if (isChecked) {
                if (!selectedFiles.contains(fileName)) {
                    selectedFiles.add(fileName);
                }
            } else {
                selectedFiles.remove(fileName);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (entry.getAttrs().isDir()) {
                String fullPath = fileName; // Just the directory name for now
                clickListener.onDirectoryClick(fullPath);
            } else {
                clickListener.onFileClick(fileName);
            }
        });



        // Handle long press to toggle checkboxes
        holder.itemView.setOnLongClickListener(v -> {
            areCheckboxesVisible = true;
            notifyDataSetChanged(); // Update all items to reflect the change
            return true; // Indicate the event was handled
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // Select all files
    public void selectAllFiles() {
        selectedFiles.clear();
        for (int i = 0; i < selectedStates.length; i++) {
            selectedStates[i] = true; // Mark all files as selected
            selectedFiles.add(files.get(i).getFilename());
        }
        areCheckboxesVisible = true; // Ensure checkboxes are visible
        notifyDataSetChanged(); // Refresh the RecyclerView
    }

    // Deselect all files
    public void deselectAllFiles() {
        for (int i = 0; i < selectedStates.length; i++) {
            selectedStates[i] = false; // Deselect all files
        }
        selectedFiles.clear(); // Clear the selected files list
        areCheckboxesVisible = true; // Keep checkboxes visible
        notifyDataSetChanged();
    }

    // Get the list of selected files
    public List<String> getSelectedFiles() {
        return new ArrayList<>(selectedFiles); // Return a copy of the selected files list
    }

    // Method to add files to the existing list
    public void addFiles(List<ChannelSftp.LsEntry> newFiles) {
        if (newFiles == null || newFiles.isEmpty()) return;

        int startPosition = files.size(); // Current size of the dataset
        files.addAll(newFiles); // Add new files to the dataset

        // Resize the selectedStates array
        boolean[] newSelectedStates = new boolean[files.size()];
        System.arraycopy(selectedStates, 0, newSelectedStates, 0, selectedStates.length);
        selectedStates = newSelectedStates;

        notifyItemRangeInserted(startPosition, newFiles.size()); // Notify adapter about new items
    }





    public void toggleSelectAll(boolean selectAll) {
        for (int i = 0; i < selectedStates.length; i++) {
            selectedStates[i] = selectAll; // Select or deselect all files
            if (selectAll) {
                selectedFiles.add(files.get(i).getFilename());
            } else {
                selectedFiles.clear(); // Clear all selections
            }
        }
        areCheckboxesVisible = true; // Ensure checkboxes are visible
        notifyDataSetChanged(); // Refresh the RecyclerView
    }
    public boolean areCheckboxesVisible() {
        return areCheckboxesVisible;
    }

    // Reset selection and hide checkboxes
    public void resetSelection() {
        areCheckboxesVisible = false;
        selectedFiles.clear(); // Clear selected files list
        for (int i = 0; i < selectedStates.length; i++) {
            selectedStates[i] = false; // Reset all selected states
        }
        notifyDataSetChanged();
    }

    public void updateFiles(List<ChannelSftp.LsEntry> newFiles) {
        files.clear();
        files.addAll(newFiles);

        // Reset selection states
        selectedStates = new boolean[files.size()];
        //selectedFiles.clear();
        areCheckboxesVisible = false;

        notifyDataSetChanged();
    }
    public void appendFiles(List<ChannelSftp.LsEntry> files) {
        int oldSize = this.files.size();
        this.files.addAll(files);
        notifyItemRangeInserted(oldSize, files.size());
    }



    public static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        CheckBox checkBox;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.file_name);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }
}
