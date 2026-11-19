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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.widget.UsageProgressBarPreference;

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_donate);

        Button donateNow = findViewById(R.id.crdroid_donate_button);
        Button dismiss = findViewById(R.id.crdroid_later_button);

        donateNow.setOnClickListener(v -> openDonatePage());
        dismiss.setOnClickListener(v -> onDismissClick());

        TextView msg = findViewById(R.id.crdroid_donate_message);
        if (msg != null) {
            msg.setMovementMethod(new ScrollingMovementMethod());
        }

        DonateReceiver.cancelNotification(this);
        setLastChecked();
        DonateReceiver.scheduleNext(this, DonateReceiver.COOLDOWN_MIN);

        FragmentManager fm = getSupportFragmentManager();
        DonateFragment fragment = (DonateFragment) fm.findFragmentByTag(DonateFragment.TAG);
        if (fragment == null) {
            fragment = new DonateFragment();
            fm.beginTransaction()
                    .replace(R.id.preference_container, fragment, DonateFragment.TAG)
                    .commitNow();
        }
        if (isNetworkAvailable(this)) {
            fetchDonationData(fragment);
        }
    }

    private void openDonatePage() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://crdroid.net/donate"));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(i);
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

    private void fetchDonationData(DonateFragment fragment) {
        executor.execute(() -> {
            JSONObject json = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL("https://crdroid.net/donation.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = new BufferedInputStream(connection.getInputStream());
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        json = new JSONObject(sb.toString());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching donation data", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final JSONObject finalJson = json;
            runOnUiThread(() -> fragment.updateFromJson(finalJson));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public static class DonateFragment extends PreferenceFragmentCompat {

        static final String TAG = "DonateFragment";
        private static final String KEY_USAGE_PREF = "donation_usage_pref";

        private UsageProgressBarPreference usagePref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
            setPreferenceScreen(screen);

            usagePref = new UsageProgressBarPreference(requireContext()) {
                @Override
                public void onBindViewHolder(PreferenceViewHolder holder) {
                    super.onBindViewHolder(holder);
                    TextView bottom = (TextView) holder.findViewById(com.android.settingslib.widget.preference.usage.R.id.bottom_summary);
                    if (bottom != null) {
                        bottom.setTextAppearance(holder.itemView.getContext(),
                            androidx.appcompat.R.style.TextAppearance_AppCompat_Subhead);
                    }
                }
            };
            usagePref.setKey(KEY_USAGE_PREF);
            usagePref.setSelectable(false);
            usagePref.setUsageSummary(getString(R.string.crdroid_donate_loading));
            usagePref.setTotalSummary("");
            usagePref.setPercent(0, 100);
            usagePref.setVisible(false);
            screen.addPreference(usagePref);
        }

        void updateFromJson(@Nullable JSONObject json) {
            if (!isAdded() || usagePref == null) return;

            if (json == null || json.has("error")) {
                usagePref.setVisible(false);
                return;
            }

            try {
                double raised = json.optDouble("raised", 0);
                double goal = json.optDouble("goal", 0);

                usagePref.setVisible(true);
                String raisedTxt = getString(R.string.crdroid_donate_raised, (int) raised);
                String totalTxt = getString(R.string.crdroid_donate_total, (int) goal);
                usagePref.setUsageSummary(raisedTxt);
                usagePref.setTotalSummary(totalTxt);
                usagePref.setPercent((long) raised, (long) Math.max(raised, goal));

                if (goal == 0 || raised >= goal) {
                    usagePref.setBottomSummary(getString(R.string.crdroid_donate_thank_you));
                    usagePref.setBottomSummaryContentDescription(getString(R.string.crdroid_donate_thank_you));
                } else {
                    double remaining = goal - raised;
                    String bottom = getString(R.string.crdroid_donate_still_needed, (int) remaining);
                    usagePref.setBottomSummary(bottom);
                    usagePref.setBottomSummaryContentDescription(bottom);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI from JSON", e);
                usagePref.setVisible(false);
            }
        }
    }
}
