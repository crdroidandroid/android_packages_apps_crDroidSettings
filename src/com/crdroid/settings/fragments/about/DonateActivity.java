/*
 * SPDX-FileCopyrightText: 2025 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.settings.fragments.about;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import lineageos.providers.LineageSettings;

import com.android.settings.R;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DonateActivity extends AppCompatActivity {

    private static final String TAG = "DonateActivity";

    private ProgressBar progressBar;
    private TextView statSummary;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_donate);

        Button donateNow = findViewById(R.id.crdroid_donate_button);
        Button dismiss = findViewById(R.id.crdroid_later_button);

        progressBar = findViewById(R.id.crdroid_donate_progress);
        statSummary = findViewById(R.id.crdroid_donate_stat_summary);

        donateNow.setOnClickListener(v -> openDonatePage());
        dismiss.setOnClickListener(v -> onDismissClick());

        DonateReceiver.cancelNotification(this);
        setLastChecked();

        if (isNetworkAvailable(this)) {
            fetchDonationData();
        }
    }

    private void openDonatePage() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://crdroid.net/donate")));
        finish();
    }

    private void onDismissClick() {
        finish();
    }

    private void setLastChecked() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(DonateReceiver.DONATE_LAST_CHECKED, System.currentTimeMillis())
                .apply();
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void fetchDonationData() {
        executor.execute(() -> {
            JSONObject json = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL("https://crdroid.net/donation.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = new BufferedInputStream(connection.getInputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();

                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    json = new JSONObject(sb.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching donation data", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final JSONObject finalJson = json;
            runOnUiThread(() -> updateUI(finalJson));
        });
    }

    private void updateUI(JSONObject json) {
        if (json == null || json.has("error")) {
            progressBar.setVisibility(View.GONE);
            statSummary.setVisibility(View.GONE);
            return;
        }

        try {
            double raised = json.getDouble("raised");
            double goal = json.getDouble("goal");

            progressBar.setVisibility(View.VISIBLE);
            statSummary.setVisibility(View.VISIBLE);

            int progress = (int) ((raised / goal) * 100);
            progressBar.setMax(100);
            progressBar.setProgress(Math.min(progress, 100));

            if (raised > goal) {
                statSummary.setText(getString(R.string.crdroid_donate_thank_you));
            } else {
                double remaining = goal - raised;
                statSummary.setText(getString(R.string.crdroid_donate_still_needed, (int) remaining));
            }
        } catch (Exception e) {
            Log.d(TAG, "json: " + json);
            Log.e(TAG, "Error: ", e);
            progressBar.setVisibility(View.GONE);
            statSummary.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
