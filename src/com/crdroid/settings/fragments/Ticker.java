package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.widget.Toast;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.ColorPickerPreference;

public class Ticker extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String PREF_SHOW_TICKER = "status_bar_show_ticker";
    private static final String PREF_TEXT_COLOR = "status_bar_ticker_text_color";
    private static final String PREF_ICON_COLOR = "status_bar_ticker_icon_color";
    private static final String PREF_TICKER_RESTORE_DEFAULTS = "ticker_restore_defaults";

    private SwitchPreference mShowTicker;
    private ColorPickerPreference mTextColor;
    private ColorPickerPreference mIconColor;
    private Preference mTickerDefaults;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ticker);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        mShowTicker = (SwitchPreference) prefSet.findPreference(PREF_SHOW_TICKER);
        mShowTicker.setChecked(Settings.System.getInt(resolver,
            Settings.System.STATUS_BAR_SHOW_TICKER, 0) != 0);
        mShowTicker.setOnPreferenceChangeListener(this);

        mTextColor = (ColorPickerPreference) prefSet.findPreference(PREF_TEXT_COLOR);
        mTextColor.setOnPreferenceChangeListener(this);
        int textColor = Settings.System.getInt(resolver,
            Settings.System.STATUS_BAR_TICKER_TEXT_COLOR, 0xffffab00);
        String textHexColor = String.format("#%08x", (0xffffab00 & textColor));
        mTextColor.setSummary(textHexColor);
        mTextColor.setNewPreviewColor(textColor);

        mIconColor = (ColorPickerPreference) prefSet.findPreference(PREF_ICON_COLOR);
        mIconColor.setOnPreferenceChangeListener(this);
        int iconColor = Settings.System.getInt(resolver,
            Settings.System.STATUS_BAR_TICKER_ICON_COLOR, 0xffffffff);
        String iconHexColor = String.format("#%08x", (0xffffffff & iconColor));
        mIconColor.setSummary(iconHexColor);
        mIconColor.setNewPreviewColor(iconColor);

        mTickerDefaults = prefSet.findPreference(PREF_TICKER_RESTORE_DEFAULTS);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mShowTicker) {
            int enabled = ((Boolean) newValue) ? 1 : 0;
            Settings.System.putInt(resolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, enabled);
            return true;
        } else if (preference == mTextColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_TEXT_COLOR, intHex);
            return true;
        } else if (preference == mIconColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                Settings.System.STATUS_BAR_TICKER_ICON_COLOR, intHex);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mTickerDefaults) {
            int intColor;
            String hexColor;

            Settings.System.putInt(resolver,
                    Settings.System.STATUS_BAR_TICKER_TEXT_COLOR, 0xffffab00);
            intColor = Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_TICKER_TEXT_COLOR, 0xffffab00);
            hexColor = String.format("#%08x", (0xffffab00 & intColor));
            mTextColor.setSummary(hexColor);
            mTextColor.setNewPreviewColor(intColor);

            Settings.System.putInt(resolver,
                    Settings.System.STATUS_BAR_TICKER_ICON_COLOR, 0xffffffff);
            intColor = Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_TICKER_ICON_COLOR, 0xffffffff);
            hexColor = String.format("#%08x", (0xffffffff & intColor));
            mIconColor.setSummary(hexColor);
            mIconColor.setNewPreviewColor(intColor);

            Toast.makeText(getActivity(), R.string.values_restored_title,
                    Toast.LENGTH_LONG).show();
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }
}
