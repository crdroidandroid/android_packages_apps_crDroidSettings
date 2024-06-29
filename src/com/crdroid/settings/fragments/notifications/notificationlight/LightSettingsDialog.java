/*
 * SPDX-FileCopyrightText: 2010 Daniel Nilsson
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.notifications.notificationlight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.lineageos.internal.notification.LedValues;
import org.lineageos.internal.notification.LightsCapabilities;
import org.lineageos.internal.notification.LineageNotification;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Locale;

public class LightSettingsDialog extends AlertDialog implements
        ColorPickerView.OnColorChangedListener, TextWatcher, OnFocusChangeListener {

    private final static String STATE_KEY_COLOR = "LightSettingsDialog:color";
    // Minimum delay between LED notification updates
    private final static long LED_UPDATE_DELAY_MS = 250;

    private ColorPickerView mColorPicker;

    private EditText mHexColorInput;
    private ColorPanelView mNewColor;
    private PulseSpeedAdapter mPulseSpeedAdapterOn;
    private PulseSpeedAdapter mPulseSpeedAdapterOff;
    private Spinner mPulseSpeedOn;
    private Spinner mPulseSpeedOff;
    private LayoutInflater mInflater;

    private NotificationManager mNotificationManager;

    private boolean mReadyForLed;
    private int mLedLastColor;
    private int mLedLastSpeedOn;
    private int mLedLastSpeedOff;

    private int mLedBrightness;
    private int mLedLastBrightness;

    private Context mContext;

    protected LightSettingsDialog(Context context, int initialColor, int initialSpeedOn,
            int initialSpeedOff) {
        super(context);

        init(context, initialColor, initialSpeedOn, initialSpeedOff, true, 0);
    }

    protected LightSettingsDialog(Context context, int initialColor, int initialSpeedOn,
            int initialSpeedOff, boolean onOffChangeable, int brightness) {
        super(context);

        init(context, initialColor, initialSpeedOn, initialSpeedOff, onOffChangeable, brightness);
    }

    private void init(Context context, int color, int speedOn, int speedOff,
            boolean onOffChangeable, int brightness) {
        mContext = context;
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        mReadyForLed = false;
        mLedLastColor = 0;

        // To fight color banding.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        setUp(color, speedOn, speedOff, onOffChangeable, brightness);
    }

    /**
     * This function sets up the dialog with the proper values.  If the speedOff parameters
     * has a -1 value disable both spinners
     *
     * @param color - the color to set
     * @param speedOn - the flash time in ms
     * @param speedOff - the flash length in ms
     */
    private void setUp(int color, int speedOn, int speedOff, boolean onOffChangeable,
               int brightness) {
        mInflater = mContext.getSystemService(LayoutInflater.class);
        View layout = mInflater.inflate(R.layout.dialog_light_settings, null);

        mColorPicker = layout.findViewById(R.id.color_picker_view);
        mHexColorInput = layout.findViewById(R.id.hex_color_input);
        mNewColor = layout.findViewById(R.id.color_panel);
        mPulseSpeedOn = layout.findViewById(R.id.on_spinner);
        mPulseSpeedOff = layout.findViewById(R.id.off_spinner);
        mColorPicker.setOnColorChangedListener(this);
        mColorPicker.setColor(color, true);

        mHexColorInput.setOnFocusChangeListener(this);

        if (onOffChangeable) {
            mPulseSpeedAdapterOn = new PulseSpeedAdapter(
                    R.array.notification_pulse_length_entries,
                    R.array.notification_pulse_length_values,
                    speedOn);
            mPulseSpeedOn.setAdapter(mPulseSpeedAdapterOn);
            mPulseSpeedOn.setSelection(mPulseSpeedAdapterOn.getTimePosition(speedOn));
            mPulseSpeedOn.setOnItemSelectedListener(mPulseSelectionListener);

            mPulseSpeedAdapterOff = new PulseSpeedAdapter(R.array.notification_pulse_speed_entries,
                    R.array.notification_pulse_speed_values,
                    speedOff);
            mPulseSpeedOff.setAdapter(mPulseSpeedAdapterOff);
            mPulseSpeedOff.setSelection(mPulseSpeedAdapterOff.getTimePosition(speedOff));
            mPulseSpeedOff.setOnItemSelectedListener(mPulseSelectionListener);
        } else {
            View speedSettingsGroup = layout.findViewById(R.id.speed_title_view);
            speedSettingsGroup.setVisibility(View.GONE);
        }

        mPulseSpeedOn.setEnabled(onOffChangeable);
        mPulseSpeedOff.setEnabled((speedOn != 1) && onOffChangeable);

        setView(layout);
        setTitle(R.string.edit_light_settings);

        if (!LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_RGB_NOTIFICATION_LED)) {
            mColorPicker.setVisibility(View.GONE);
            LinearLayout colorPanel = layout.findViewById(R.id.color_panel_view);
            colorPanel.setVisibility(View.GONE);
            View lightsDialogDivider = layout.findViewById(R.id.lights_dialog_divider);
            lightsDialogDivider.setVisibility(View.GONE);
        }

        mLedBrightness = brightness;
        mLedLastBrightness = -1; // out of range

        mReadyForLed = true;
        updateLed();
    }

    private final AdapterView.OnItemSelectedListener mPulseSelectionListener =
            new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (parent == mPulseSpeedOn) {
                mPulseSpeedOff.setEnabled(mPulseSpeedOn.isEnabled() && getPulseSpeedOn() != 1);
            }
            updateLed();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(STATE_KEY_COLOR, getColor());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mColorPicker.setColor(state.getInt(STATE_KEY_COLOR), true);
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissLed();
    }

    @Override
    public void onStart() {
        super.onStart();
        updateLed();
    }

    @Override
    public void onColorChanged(int color) {
        final boolean hasAlpha = mColorPicker.isAlphaSliderVisible();
        final String format = hasAlpha ? "%08x" : "%06x";
        final int mask = hasAlpha ? 0xFFFFFFFF : 0x00FFFFFF;

        mNewColor.setColor(color);
        mHexColorInput.setText(String.format(Locale.US, format, color & mask));

        updateLed();
    }

    public void setAlphaSliderVisible(boolean visible) {
        mHexColorInput.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(visible ? 8 : 6) } );
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    public void setColor(int color) {
        mColorPicker.setColor(color, true);
    }

    @SuppressWarnings("unchecked")
    public int getPulseSpeedOn() {
        if (mPulseSpeedOn.isEnabled()) {
            return ((Pair<String, Integer>) mPulseSpeedOn.getSelectedItem()).second;
        } else {
            return 1;
        }
    }

    public void setPulseSpeedOn(int speedOn) {
        mPulseSpeedOn.setSelection(mPulseSpeedAdapterOn.getTimePosition(speedOn));
    }

    @SuppressWarnings("unchecked")
    public int getPulseSpeedOff() {
        // return 0 if 'Always on' is selected
        return getPulseSpeedOn() == 1
                ? 0
                : ((Pair<String, Integer>) mPulseSpeedOff.getSelectedItem()).second;
    }

    public void setPulseSpeedOff(int speedOff) {
        mPulseSpeedOff.setSelection(mPulseSpeedAdapterOff.getTimePosition(speedOff));
    }

    private final Handler mLedHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            updateLed();
        }
    };

    private void updateLed() {
        if (!mReadyForLed) {
            return;
        }

        final int color = getColor() & 0xFFFFFF;
        final int speedOn, speedOff;
        if (mPulseSpeedOn.isEnabled()) {
            speedOn = getPulseSpeedOn();
            speedOff = getPulseSpeedOff();
        } else {
            speedOn = 1;
            speedOff = 0;
        }

        if (mLedLastColor == color && mLedLastSpeedOn == speedOn && mLedLastSpeedOff == speedOff
                && mLedLastBrightness == mLedBrightness) {
            return;
        }

        // Dampen rate of consecutive LED changes
        if (mLedHandler.hasMessages(0)) {
            return;
        }
        mLedHandler.sendEmptyMessageDelayed(0, LED_UPDATE_DELAY_MS);

        // Set a notification to display the LED color
        final Bundle b = new Bundle();
        b.putBoolean(LineageNotification.EXTRA_FORCE_SHOW_LIGHTS, true);
        if  (mLedBrightness > 0 && mLedBrightness < LedValues.LIGHT_BRIGHTNESS_MAXIMUM) {
            b.putInt(LineageNotification.EXTRA_FORCE_LIGHT_BRIGHTNESS, mLedBrightness);
        }
        b.putInt(LineageNotification.EXTRA_FORCE_COLOR, color);
        b.putInt(LineageNotification.EXTRA_FORCE_LIGHT_ON_MS, speedOn);
        b.putInt(LineageNotification.EXTRA_FORCE_LIGHT_OFF_MS, speedOff);

        createNotificationChannel();

        final String channelId = mContext.getString(R.string.channel_light_settings_id);
        final Notification.Builder builder = new Notification.Builder(mContext, channelId);
        builder.setLights(color, speedOn, speedOff);
        builder.setExtras(b);
        builder.setSmallIcon(R.drawable.ic_settings_24dp);
        builder.setContentTitle(mContext.getString(R.string.led_notification_title));
        builder.setContentText(mContext.getString(R.string.led_notification_text));
        builder.setOngoing(true);

        final Notification notification = builder.build();
        mNotificationManager.notify(channelId, 1, notification);

        mLedLastColor = color;
        mLedLastSpeedOn = speedOn;
        mLedLastSpeedOff = speedOff;
        mLedLastBrightness = mLedBrightness;
    }

    public void dismissLed() {
        final String channelId = mContext.getString(R.string.channel_light_settings_id);
        mNotificationManager.cancel(channelId, 1);
        // ensure we later reset LED if dialog is
        // hidden and then made visible
        mLedLastColor = 0;
    }

    private void createNotificationChannel() {
        final String channelId = mContext.getString(R.string.channel_light_settings_id);
        final String channelName = mContext.getString(R.string.channel_light_settings_name);
        final NotificationChannel notificationChannel = new NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.enableLights(true);
        notificationChannel.enableVibration(false);
        notificationChannel.setShowBadge(false);

        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    class PulseSpeedAdapter extends BaseAdapter implements SpinnerAdapter {
        private final ArrayList<Pair<String, Integer>> times;

        public PulseSpeedAdapter(int timeNamesResource, int timeValuesResource) {
            times = new ArrayList<>();

            String[] time_names = mContext.getResources().getStringArray(timeNamesResource);
            String[] time_values = mContext.getResources().getStringArray(timeValuesResource);

            for(int i = 0; i < time_values.length; ++i) {
                times.add(new Pair<>(time_names[i], Integer.decode(time_values[i])));
            }

        }

        /**
         * This constructor apart from taking a usual time entry array takes the
         * currently configured time value which might cause the addition of a
         * "Custom" time entry in the spinner in case this time value does not
         * match any of the predefined ones in the array.
         *
         * @param timeNamesResource The time entry names array
         * @param timeValuesResource The time entry values array
         * @param customTime Current time value that might be one of the
         *            predefined values or a totally custom value
         */
        public PulseSpeedAdapter(int timeNamesResource, int timeValuesResource,
                                 Integer customTime) {
            this(timeNamesResource, timeValuesResource);

            // Check if we also need to add the custom value entry
            if (getTimePosition(customTime) == -1) {
                times.add(new Pair<>(mContext.getResources()
                        .getString(R.string.custom_time), customTime));
            }
        }

        /**
         * Will return the position of the spinner entry with the specified
         * time. Returns -1 if there is no such entry.
         *
         * @param time Time in ms
         * @return Position of entry with given time or -1 if not found.
         */
        public int getTimePosition(Integer time) {
            for (int position = 0; position < getCount(); ++position) {
                if (getItem(position).second.equals(time)) {
                    return position;
                }
            }

            return -1;
        }

        @Override
        public int getCount() {
            return times.size();
        }

        @Override
        public Pair<String, Integer> getItem(int position) {
            return times.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mInflater.inflate(R.layout.pulse_time_item, parent, false);
            }

            Pair<String, Integer> entry = getItem(position);
            ((TextView) view.findViewById(R.id.textViewName)).setText(entry.first);

            return view;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        String hexColor = mHexColorInput.getText().toString();
        if (!hexColor.isEmpty()) {
            try {
                int color = Color.parseColor('#' + hexColor);
                if (!mColorPicker.isAlphaSliderVisible()) {
                    color |= 0xFF000000; // set opaque
                }
                mColorPicker.setColor(color);
                mNewColor.setColor(color);
                updateLed();
            } catch (IllegalArgumentException ex) {
                // Number format is incorrect, ignore
            }
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            mHexColorInput.removeTextChangedListener(this);
            InputMethodManager inputMethodManager =
                    mContext.getSystemService(InputMethodManager.class);
            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } else {
            mHexColorInput.addTextChangedListener(this);
        }
    }
}
