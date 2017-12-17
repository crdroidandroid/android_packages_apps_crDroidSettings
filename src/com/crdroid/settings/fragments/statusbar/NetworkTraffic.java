/*
 * Copyright (C) 2016-2017 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class NetworkTraffic extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "NetworkTraffic";

    private static final String NETWORK_TRAFFIC_STATE = "network_traffic_state";
    private static final String NETWORK_TRAFFIC_UNIT = "network_traffic_unit";
    private static final String NETWORK_TRAFFIC_PERIOD = "network_traffic_period";
    private static final String NETWORK_TRAFFIC_HIDEARROW = "network_traffic_hidearrow";
    private static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";
    private static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD = "network_traffic_autohide_threshold";

    private int mNetTrafficVal;
    private int MASK_UP;
    private int MASK_DOWN;
    private int MASK_UNIT;
    private int MASK_PERIOD;

    private ListPreference mNetTrafficState;
    private ListPreference mNetTrafficUnit;
    private ListPreference mNetTrafficPeriod;
    private SwitchPreference mNetTrafficHidearrow;
    private SwitchPreference mNetTrafficAutohide;
    private CustomSeekBarPreference mNetTrafficAutohideThreshold;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.network_traffic);

        ContentResolver resolver = mContext.getContentResolver();

        loadResources(mContext);

        mNetTrafficState = (ListPreference) findPreference(NETWORK_TRAFFIC_STATE);
        mNetTrafficUnit = (ListPreference) findPreference(NETWORK_TRAFFIC_UNIT);
        mNetTrafficPeriod = (ListPreference) findPreference(NETWORK_TRAFFIC_PERIOD);

        mNetTrafficHidearrow =
            (SwitchPreference) findPreference(NETWORK_TRAFFIC_HIDEARROW);
        mNetTrafficHidearrow.setChecked((Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_HIDEARROW, 0, UserHandle.USER_CURRENT) == 1));
        mNetTrafficHidearrow.setOnPreferenceChangeListener(this);

        mNetTrafficAutohide =
            (SwitchPreference) findPreference(NETWORK_TRAFFIC_AUTOHIDE);
        mNetTrafficAutohide.setChecked((Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT) == 1));
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficAutohideThreshold = (CustomSeekBarPreference) findPreference(NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
        int netTrafficAutohideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 10, UserHandle.USER_CURRENT);
        mNetTrafficAutohideThreshold.setValue(netTrafficAutohideThreshold / 1);
        mNetTrafficAutohideThreshold.setOnPreferenceChangeListener(this);

 	// TrafficStats will return UNSUPPORTED if the device does not support it.
        if (TrafficStats.getTotalTxBytes() != TrafficStats.UNSUPPORTED &&
                TrafficStats.getTotalRxBytes() != TrafficStats.UNSUPPORTED) {
            mNetTrafficVal = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0, UserHandle.USER_CURRENT);
            int intIndex = mNetTrafficVal & (MASK_UP + MASK_DOWN);
            intIndex = mNetTrafficState.findIndexOfValue(String.valueOf(intIndex));
            if (intIndex <= 0) {
                mNetTrafficUnit.setEnabled(false);
                mNetTrafficPeriod.setEnabled(false);
                mNetTrafficHidearrow.setEnabled(false);
                mNetTrafficAutohide.setEnabled(false);
                mNetTrafficAutohideThreshold.setEnabled(false);
            }
            mNetTrafficState.setValueIndex(intIndex >= 0 ? intIndex : 0);
            mNetTrafficState.setSummary(mNetTrafficState.getEntry());
            mNetTrafficState.setOnPreferenceChangeListener(this);

            mNetTrafficUnit.setValueIndex(getBit(mNetTrafficVal, MASK_UNIT) ? 1 : 0);
            mNetTrafficUnit.setSummary(mNetTrafficUnit.getEntry());
            mNetTrafficUnit.setOnPreferenceChangeListener(this);

            intIndex = (mNetTrafficVal & MASK_PERIOD) >>> 16;
            intIndex = mNetTrafficPeriod.findIndexOfValue(String.valueOf(intIndex));
            mNetTrafficPeriod.setValueIndex(intIndex >= 0 ? intIndex : 1);
            mNetTrafficPeriod.setSummary(mNetTrafficPeriod.getEntry());
            mNetTrafficPeriod.setOnPreferenceChangeListener(this);
        }
    }

    private void updateNetworkTrafficState(int mIndex) {
        if (mIndex <= 0) {
            mNetTrafficUnit.setEnabled(false);
            mNetTrafficPeriod.setEnabled(false);
            mNetTrafficHidearrow.setEnabled(false);
            mNetTrafficAutohide.setEnabled(false);
            mNetTrafficAutohideThreshold.setEnabled(false);
        } else {
            mNetTrafficUnit.setEnabled(true);
            mNetTrafficPeriod.setEnabled(true);
            mNetTrafficHidearrow.setEnabled(true);
            mNetTrafficAutohide.setEnabled(true);
            mNetTrafficAutohideThreshold.setEnabled(true);
        }
    }
   
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mNetTrafficState) {
            int intState = Integer.parseInt((String)newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_UP, getBit(intState, MASK_UP));
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_DOWN, getBit(intState, MASK_DOWN));
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_STATE, mNetTrafficVal, UserHandle.USER_CURRENT);
            int index = mNetTrafficState.findIndexOfValue((String) newValue);
            mNetTrafficState.setSummary(mNetTrafficState.getEntries()[index]);
            updateNetworkTrafficState(index);
            return true;
        } else if (preference == mNetTrafficUnit) {
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_UNIT, ((String)newValue).equals("1"));
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_STATE, mNetTrafficVal, UserHandle.USER_CURRENT);
            int index = mNetTrafficUnit.findIndexOfValue((String) newValue);
            mNetTrafficUnit.setSummary(mNetTrafficUnit.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficPeriod) {
            int intState = Integer.parseInt((String)newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_PERIOD, false) + (intState << 16);
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_STATE, mNetTrafficVal, UserHandle.USER_CURRENT);
            int index = mNetTrafficPeriod.findIndexOfValue((String) newValue);
            mNetTrafficPeriod.setSummary(mNetTrafficPeriod.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficHidearrow) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_HIDEARROW, value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mNetTrafficAutohide) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE, value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mNetTrafficAutohideThreshold) {
            int threshold = (Integer) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, threshold, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    private void loadResources(Context mContext) {
        Resources resources = mContext.getResources();
        MASK_UP = resources.getInteger(R.integer.maskUp);
        MASK_DOWN = resources.getInteger(R.integer.maskDown);
        MASK_UNIT = resources.getInteger(R.integer.maskUnit);
        MASK_PERIOD = resources.getInteger(R.integer.maskPeriod);
    }

    private int setBit(int intNumber, int intMask, boolean blnState) {
        if (blnState) {
            return (intNumber | intMask);
        }
        return (intNumber & ~intMask);
    }

    private boolean getBit(int intNumber, int intMask) {
        return (intNumber & intMask) == intMask;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_HIDEARROW, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 10, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
