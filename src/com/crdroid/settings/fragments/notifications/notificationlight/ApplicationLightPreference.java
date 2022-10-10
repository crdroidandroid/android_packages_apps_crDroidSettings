/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments.notifications.notificationlight;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.internal.notification.LightsCapabilities;
import com.crdroid.settings.preferences.CustomDialogPref;
import com.android.settings.R;

public class ApplicationLightPreference extends CustomDialogPref<LightSettingsDialog>
        implements View.OnLongClickListener {

    private static String TAG = "AppLightPreference";
    public static final int DEFAULT_TIME = 1000;
    public static final int DEFAULT_COLOR = 0xffffff;

    private ImageView mLightColorView;
    private TextView mOnValueView;
    private TextView mOffValueView;

    private int mColorValue;
    private int mOnValue;
    private int mOffValue;
    private boolean mOnOffChangeable;

    private boolean mHasDefaults;
    private int mDefaultColorValue;
    private int mDefaultOnValue;
    private int mDefaultOffValue;

    private int mLedBrightness;

    private LightSettingsDialog mDialog;

    public interface ItemLongClickListener {
        public boolean onItemLongClick(String key);
    }

    private ItemLongClickListener mLongClickListener;

    /**
     * @param context
     * @param attrs
     */
    public ApplicationLightPreference(Context context, AttributeSet attrs) {
        this(context, attrs, DEFAULT_COLOR, DEFAULT_TIME, DEFAULT_TIME);
    }

    /**
     * @param context
     * @param attrs
     * @param color
     * @param onValue
     * @param offValue
     */
    public ApplicationLightPreference(Context context, AttributeSet attrs,
                                      int color, int onValue, int offValue) {
        this(context, attrs, color, onValue, offValue,
                LightsCapabilities.supports(context, LightsCapabilities.LIGHTS_PULSATING_LED));
    }

    /**
     * @param context
     * @param attrs
     * @param color
     * @param onValue
     * @param offValue
     * @param onOffChangeable
     */
    public ApplicationLightPreference(Context context, AttributeSet attrs,
                                      int color, int onValue, int offValue, boolean onOffChangeable) {
        super(context, attrs);
        mColorValue = color;
        mOnValue = onValue;
        mOffValue = offValue;
        mOnOffChangeable = onOffChangeable;
        mHasDefaults = false;
        mLedBrightness = 0; // use system brightness

        setWidgetLayoutResource(R.layout.preference_application_light);
    }

    @Override
    public boolean onLongClick(View view) {
        if (mLongClickListener != null) {
            return mLongClickListener.onItemLongClick(getKey());
        }
        return false;
    }

    public void setOnLongClickListener(ItemLongClickListener l) {
        mLongClickListener = l;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mLightColorView = (ImageView) holder.findViewById(R.id.light_color);
        mOnValueView = (TextView) holder.findViewById(R.id.textViewTimeOnValue);
        mOffValueView = (TextView) holder.findViewById(R.id.textViewTimeOffValue);

        // Hide the summary text - it takes up too much space on a low res device
        // We use it for storing the package name for the longClickListener
        TextView tView = (TextView) holder.findViewById(android.R.id.summary);
        tView.setVisibility(View.GONE);

        if (!LightsCapabilities.supports(
                getContext(), LightsCapabilities.LIGHTS_RGB_NOTIFICATION_LED)) {
            mLightColorView.setVisibility(View.GONE);
        }

        updatePreferenceViews();
        holder.itemView.setOnLongClickListener(this);
    }

    public void onStop() {
        if (getDialog() != null) {
            getDialog().onStop();
        }
    }

    public void onStart() {
        if (getDialog() != null) {
            getDialog().onStart();
        }
    }

    private void updatePreferenceViews() {
        final int size = (int) getContext().getResources().getDimension(R.dimen.oval_notification_size);

        if (mLightColorView != null) {
            mLightColorView.setEnabled(true);
            // adjust if necessary to prevent material whiteout
            final int imageColor = ((mColorValue & 0xF0F0F0) == 0xF0F0F0) ?
                    (mColorValue - 0x101010) : mColorValue;
            mLightColorView.setImageDrawable(createOvalShape(size,
                    0xFF000000 + imageColor));
        }
        if (mOnValueView != null) {
            mOnValueView.setText(mapLengthValue(mOnValue));
        }
        if (mOffValueView != null) {
            if (mOnValue == 1 || !mOnOffChangeable) {
                mOffValueView.setVisibility(View.GONE);
            } else {
                mOffValueView.setVisibility(View.VISIBLE);
            }
            mOffValueView.setText(mapSpeedValue(mOffValue));
        }
    }

    @Override
    protected boolean onDismissDialog(LightSettingsDialog dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            // Reset to previously supplied defaults
            mDialog.setColor(mDefaultColorValue);
            if (mOnOffChangeable) {
                mDialog.setPulseSpeedOn(mDefaultOnValue);
                mDialog.setPulseSpeedOff(mDefaultOffValue);
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            mColorValue = mDialog.getColor() & 0x00FFFFFF; // strip alpha, led does not support it
            mOnValue = mDialog.getPulseSpeedOn();
            mOffValue = mDialog.getPulseSpeedOff();
            updatePreferenceViews();
            callChangeListener(null);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mDialog = new LightSettingsDialog(getContext(), 0xFF000000 | mColorValue,
                mOnValue, mOffValue, mOnOffChangeable, mLedBrightness);
        mDialog.setAlphaSliderVisible(false);

        // Initialize the buttons with null handlers, as they will get remapped by
        // CustomPreferenceDialogFragment
        mDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getContext().getResources().getString(R.string.dlg_ok),
                (DialogInterface.OnClickListener) null);
        mDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                getContext().getResources().getString(R.string.cancel),
                (DialogInterface.OnClickListener) null);
        if (mHasDefaults) {
            mDialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                    getContext().getResources().getString(R.string.reset),
                    (DialogInterface.OnClickListener) null);
        }

        return mDialog;
    }


    /**
     * Getters and Setters
     */
    public int getColor() {
        return mColorValue;
    }

    public void setColor(int color) {
        mColorValue = color;
        updatePreferenceViews();
    }

    public void setOnValue(int value) {
        mOnValue = value;
        updatePreferenceViews();
    }

    public int getOnValue() {
        return mOnValue;
    }

    public void setOffValue(int value) {
        mOffValue = value;
        updatePreferenceViews();
    }

    public int getOffValue() {
        return mOffValue;
    }

    public void setAllValues(int color, int onValue, int offValue) {
        mColorValue = color;
        mOnValue = onValue;
        mOffValue = offValue;
        updatePreferenceViews();
    }

    public void setAllValues(int color, int onValue, int offValue, boolean onOffChangeable) {
        mColorValue = color;
        mOnValue = onValue;
        mOffValue = offValue;
        mOnOffChangeable = onOffChangeable;
        updatePreferenceViews();
    }

    public void setOnOffValue(int onValue, int offValue) {
        mOnValue = onValue;
        mOffValue = offValue;
        updatePreferenceViews();
    }

    public void setOnOffChangeable(boolean value) {
        mOnOffChangeable = value;
    }

    public void setDefaultValues(int color, int onValue, int offValue) {
        mDefaultColorValue = color;
        mDefaultOnValue = onValue;
        mDefaultOffValue = offValue;
        mHasDefaults = true;
    }

    public void setBrightness(int brightness) {
        mLedBrightness = brightness;
    }

    /**
     * Utility methods
     */
    private static ShapeDrawable createOvalShape(int size, int color) {
        ShapeDrawable shape = new ShapeDrawable(new OvalShape());
        shape.setIntrinsicHeight(size);
        shape.setIntrinsicWidth(size);
        shape.getPaint().setColor(color);
        return shape;
    }

    private String mapLengthValue(Integer time) {
        if (!mOnOffChangeable) {
            return getContext().getResources().getString(R.string.pulse_length_always_on);
        }
        if (time == DEFAULT_TIME) {
            return getContext().getResources().getString(R.string.default_time);
        }

        String[] timeNames = getContext().getResources().getStringArray(R.array.notification_pulse_length_entries);
        String[] timeValues = getContext().getResources().getStringArray(R.array.notification_pulse_length_values);

        for (int i = 0; i < timeValues.length; i++) {
            if (Integer.decode(timeValues[i]).equals(time)) {
                return timeNames[i];
            }
        }

        return getContext().getResources().getString(R.string.custom_time);
    }

    private String mapSpeedValue(Integer time) {
        if (time == DEFAULT_TIME) {
            return getContext().getResources().getString(R.string.default_time);
        }

        String[] timeNames = getContext().getResources().getStringArray(R.array.notification_pulse_speed_entries);
        String[] timeValues = getContext().getResources().getStringArray(R.array.notification_pulse_speed_values);

        for (int i = 0; i < timeValues.length; i++) {
            if (Integer.decode(timeValues[i]).equals(time)) {
                return timeNames[i];
            }
        }

        return getContext().getResources().getString(R.string.custom_time);
    }
}
