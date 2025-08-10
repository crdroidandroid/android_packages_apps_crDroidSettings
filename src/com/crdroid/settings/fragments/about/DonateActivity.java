/*
 * SPDX-FileCopyrightText: 2025 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.settings.fragments.about;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import lineageos.providers.LineageSettings;

import com.android.settings.R;

public class DonateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_donate);

        Button donateNow = findViewById(R.id.crdroid_donate_button);
        Button dismiss = findViewById(R.id.crdroid_later_button);

        donateNow.setOnClickListener(v -> openDonatePage());
        dismiss.setOnClickListener(v -> onDismissClick());

        DonateReceiver.cancelNotification(this);
        setLastChecked();
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
}
