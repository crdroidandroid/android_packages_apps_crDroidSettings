package com.crdroid.settings.fragments.notifications.notificationlight;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.os.UserHandle;
import android.provider.Settings;
import android.os.Bundle;
import android.widget.Toast;
import com.android.settings.R;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.settings.SettingsPreferenceFragment;
import net.margaritov.preference.colorpicker.ColorPickerPreference;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;

import com.android.internal.logging.nano.MetricsProto;

public class AmbientStuffSettings extends SettingsPreferenceFragment
                         implements OnPreferenceChangeListener {

    private ColorPickerPreference mEdgeLightColorPreference;
    private SystemSettingSeekBarPreference mEdgeLightDurationPreference;
    private SystemSettingSeekBarPreference mEdgeLightRepeatCountPreference;
    private ListPreference mColorMode;

    private static final String NOTIFICATION_PULSE_COLOR = "ambient_notification_light_color";
    private static final String AMBIENT_LIGHT_DURATION = "ambient_light_duration";
    private static final String AMBIENT_LIGHT_REPEAT_COUNT = "ambient_light_repeat_count";
    private static final String PULSE_COLOR_MODE_PREF = "ambient_notification_light_color_mode";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ContentResolver resolver = getActivity().getContentResolver();
        addPreferencesFromResource(R.xml.ambient_stuff);

        PreferenceScreen prefScreen = getPreferenceScreen();

        mEdgeLightRepeatCountPreference = (SystemSettingSeekBarPreference) findPreference(AMBIENT_LIGHT_REPEAT_COUNT);
        mEdgeLightRepeatCountPreference.setOnPreferenceChangeListener(this);
        int rCount = Settings.System.getInt(getContentResolver(),
                Settings.System.AMBIENT_LIGHT_REPEAT_COUNT, 0);
        mEdgeLightRepeatCountPreference.setValue(rCount);

        mEdgeLightDurationPreference = (SystemSettingSeekBarPreference) findPreference(AMBIENT_LIGHT_DURATION);
        mEdgeLightDurationPreference.setOnPreferenceChangeListener(this);
        int duration = Settings.System.getInt(getContentResolver(),
                Settings.System.AMBIENT_LIGHT_DURATION, 2);
        mEdgeLightDurationPreference.setValue(duration);

        mColorMode = (ListPreference) findPreference(PULSE_COLOR_MODE_PREF);
        int value;
        boolean colorModeAutomatic = Settings.System.getInt(getContentResolver(),
                Settings.System.NOTIFICATION_PULSE_COLOR_AUTOMATIC, 0) != 0;
        boolean colorModeAccent = Settings.System.getInt(getContentResolver(),
                Settings.System.NOTIFICATION_PULSE_ACCENT, 0) != 0;
        if (colorModeAutomatic) {
            value = 0;
        } else if (colorModeAccent) {
            value = 1;
        } else {
            value = 2;
        }

        mColorMode.setValue(Integer.toString(value));
        mColorMode.setSummary(mColorMode.getEntry());
        mColorMode.setOnPreferenceChangeListener(this);

        mEdgeLightColorPreference = (ColorPickerPreference) findPreference(NOTIFICATION_PULSE_COLOR);
        int edgeLightColor = Settings.System.getInt(getContentResolver(),
                Settings.System.NOTIFICATION_PULSE_COLOR, 0xFF3980FF);
        mEdgeLightColorPreference.setNewPreviewColor(edgeLightColor);
        mEdgeLightColorPreference.setAlphaSliderEnabled(false);
        String edgeLightColorHex = String.format("#%08x", (0xFF3980FF & edgeLightColor));
        if (edgeLightColorHex.equals("#ff3980ff")) {
            mEdgeLightColorPreference.setSummary(R.string.color_default);
        } else {
            mEdgeLightColorPreference.setSummary(edgeLightColorHex);
        }
        mEdgeLightColorPreference.setOnPreferenceChangeListener(this);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
         ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mEdgeLightColorPreference) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            if (hex.equals("#ff3980ff")) {
                preference.setSummary(R.string.color_default);
            } else {
                preference.setSummary(hex);
            }
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_PULSE_COLOR, intHex);
            return true;
        } else if (preference == mEdgeLightRepeatCountPreference) {
                int value = (Integer) newValue;
                Settings.System.putInt(getContentResolver(),
                        Settings.System.AMBIENT_LIGHT_REPEAT_COUNT, value);
                return true;
        } else if (preference == mEdgeLightDurationPreference) {
            int value = (Integer) newValue;
                Settings.System.putInt(getContentResolver(),
                    Settings.System.AMBIENT_LIGHT_DURATION, value);
            return true;
        } else if (preference == mColorMode) {
             int value = Integer.valueOf((String) newValue);
            int index = mColorMode.findIndexOfValue((String) newValue);
            mColorMode.setSummary(mColorMode.getEntries()[index]);
            if (value == 0) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_COLOR_AUTOMATIC, 1);
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_ACCENT, 0);
            } else if (value == 1) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_COLOR_AUTOMATIC, 0);
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_ACCENT, 1);
            } else {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_COLOR_AUTOMATIC, 0);
                Settings.System.putInt(getContentResolver(),
                        Settings.System.NOTIFICATION_PULSE_ACCENT, 0);
            }
            return true;
        }
         return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
