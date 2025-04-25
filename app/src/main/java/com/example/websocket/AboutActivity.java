package com.example.websocket;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_about);

        // Optional: Set the title of the activity
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("About");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Add a back button
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Handle the back button in the toolbar
        return true;
    }
}
