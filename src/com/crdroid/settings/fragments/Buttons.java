/*
 * Copyright (C) 2016-2020 crDroid Android Project
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
package com.crdroid.settings.fragments;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.WindowManagerGlobal;

import androidx.preference.SwitchPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.buttons.ButtonBacklightBrightness;
import com.crdroid.settings.fragments.buttons.PowerMenuActions;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.utils.DeviceUtils;
import com.crdroid.settings.utils.TelephonyUtils;

import org.lineageos.internal.util.ScreenType;

import static org.lineageos.internal.util.DeviceKeysConstants.*;

import java.util.List;
import java.util.ArrayList;

import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

@SearchIndexable
public class Buttons extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {
    private static final String TAG = "SystemSettings";

    private static final String HWKEYS_DISABLED = "hardware_keys_disable";
    private static final String KEY_ANBI = "anbi_enabled";
    private static final String KEY_BUTTON_BACKLIGHT = "button_backlight";
    private static final String KEY_HOME_LONG_PRESS = "hardware_keys_home_long_press";
    private static final String KEY_HOME_DOUBLE_TAP = "hardware_keys_home_double_tap";
    private static final String KEY_MENU_WAKE_SCREEN = "menu_wake_screen";
    private static final String KEY_MENU_PRESS = "hardware_keys_menu_press";
    private static final String KEY_MENU_LONG_PRESS = "hardware_keys_menu_long_press";
    private static final String KEY_ASSIST_WAKE_SCREEN = "assist_wake_screen";
    private static final String KEY_ASSIST_PRESS = "hardware_keys_assist_press";
    private static final String KEY_ASSIST_LONG_PRESS = "hardware_keys_assist_long_press";
    private static final String KEY_APP_SWITCH_WAKE_SCREEN = "app_switch_wake_screen";
    private static final String KEY_APP_SWITCH_PRESS = "hardware_keys_app_switch_press";
    private static final String KEY_APP_SWITCH_LONG_PRESS = "hardware_keys_app_switch_long_press";
    private static final String KEY_SCREENSHOT_DELAY = "screenshot_delay";
    private static final String KEY_VOLUME_WAKE_SCREEN = "volume_wake_screen";
    private static final String KEY_VOLUME_ANSWER_CALL = "volume_answer_call";
    private static final String KEY_VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";
    private static final String KEY_SWAP_VOLUME_BUTTONS = "swap_volume_buttons";
    private static final String KEY_POWER_MENU = "power_menu";
    private static final String KEY_POWER_END_CALL = "power_end_call";
    private static final String KEY_HOME_WAKE_SCREEN = "home_wake_screen";
    private static final String KEY_HOME_ANSWER_CALL = "home_answer_call";
    private static final String KEY_VOLUME_MUSIC_CONTROLS = "volbtn_music_controls";
    private static final String KEY_TORCH_LONG_PRESS_POWER_GESTURE =
            "torch_long_press_power_gesture";
    private static final String KEY_TORCH_LONG_PRESS_POWER_TIMEOUT =
            "torch_long_press_power_timeout";
    private static final String KEY_BACK_WAKE_SCREEN = "back_wake_screen";
    private static final String KEY_BACK_LONG_PRESS = "hardware_keys_back_long_press";
    private static final String KEY_CAMERA_WAKE_SCREEN = "camera_wake_screen";
    private static final String KEY_CAMERA_SLEEP_ON_RELEASE = "camera_sleep_on_release";
    private static final String KEY_CAMERA_LAUNCH = "camera_launch";
    private static final String KEY_ADDITIONAL_BUTTONS = "additional_buttons";

    private static final String CATEGORY_POWER = "power_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_BACK = "back_key";
    private static final String CATEGORY_MENU = "menu_key";
    private static final String CATEGORY_ASSIST = "assist_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_CAMERA = "camera_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_BACKLIGHT = "key_backlight";

    private SwitchPreference mHardwareKeysDisable;
    private SwitchPreference mAnbi;
    private ListPreference mHomeLongPressAction;
    private ListPreference mHomeDoubleTapAction;
    private ListPreference mBackLongPressAction;
    private ListPreference mMenuPressAction;
    private ListPreference mMenuLongPressAction;
    private ListPreference mAssistPressAction;
    private ListPreference mAssistLongPressAction;
    private ListPreference mAppSwitchPressAction;
    private ListPreference mAppSwitchLongPressAction;
    private SwitchPreference mCameraWakeScreen;
    private SwitchPreference mCameraSleepOnRelease;
    private SwitchPreference mCameraLaunch;
    private ListPreference mVolumeKeyCursorControl;
    private SwitchPreference mVolumeWakeScreen;
    private SwitchPreference mVolumeMusicControls;
    private SwitchPreference mSwapVolumeButtons;
    private SwitchPreference mPowerEndCall;
    private SwitchPreference mHomeAnswerCall;
    private SwitchPreference mTorchLongPressPowerGesture;
    private ListPreference mTorchLongPressPowerTimeout;
    private ButtonBacklightBrightness backlight;
    private CustomSeekBarPreference mScreenshotDelay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_button);

        final Resources res = getResources();
        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final boolean hasPowerKey = DeviceUtils.hasPowerKey();
        final boolean hasHomeKey = DeviceUtils.hasHomeKey(getActivity());
        final boolean hasBackKey = DeviceUtils.hasBackKey(getActivity());
        final boolean hasMenuKey = DeviceUtils.hasMenuKey(getActivity());
        final boolean hasAssistKey = DeviceUtils.hasAssistKey(getActivity());
        final boolean hasAppSwitchKey = DeviceUtils.hasAppSwitchKey(getActivity());
        final boolean hasCameraKey = DeviceUtils.hasCameraKey(getActivity());
        final boolean hasVolumeKeys = DeviceUtils.hasVolumeKeys(getActivity());

        final boolean showHomeWake = DeviceUtils.canWakeUsingHomeKey(getActivity());
        final boolean showBackWake = DeviceUtils.canWakeUsingBackKey(getActivity());
        final boolean showMenuWake = DeviceUtils.canWakeUsingMenuKey(getActivity());
        final boolean showAssistWake = DeviceUtils.canWakeUsingAssistKey(getActivity());
        final boolean showAppSwitchWake = DeviceUtils.canWakeUsingAppSwitchKey(getActivity());
        final boolean showCameraWake = DeviceUtils.canWakeUsingCameraKey(getActivity());
        final boolean showVolumeWake = DeviceUtils.canWakeUsingVolumeKeys(getActivity());

        boolean hasAnyBindableKey = false;
        final PreferenceCategory powerCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_POWER);
        final PreferenceCategory homeCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_HOME);
        final PreferenceCategory backCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_BACK);
        final PreferenceCategory menuCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_MENU);
        final PreferenceCategory assistCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_ASSIST);
        final PreferenceCategory appSwitchCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_APPSWITCH);
        final PreferenceCategory volumeCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_VOLUME);
        final PreferenceCategory cameraCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_CAMERA);

        LineageHardwareManager mLineageHardware = LineageHardwareManager.getInstance(getActivity());
        mHardwareKeysDisable = (SwitchPreference) findPreference(HWKEYS_DISABLED);

        if (mLineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE)) {
            mHardwareKeysDisable.setOnPreferenceChangeListener(this);
        } else {
            prefScreen.removePreference(mHardwareKeysDisable);
        }

        mAnbi = (SwitchPreference) findPreference(KEY_ANBI);

        // Power button ends calls.
        mPowerEndCall = (SwitchPreference) findPreference(KEY_POWER_END_CALL);

        // Long press power while display is off to activate torchlight
        mTorchLongPressPowerGesture =
                (SwitchPreference) findPreference(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
        final int torchLongPressPowerTimeout = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.TORCH_LONG_PRESS_POWER_TIMEOUT, 0, UserHandle.USER_CURRENT);
        mTorchLongPressPowerTimeout = initList(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT,
                torchLongPressPowerTimeout);

        // Home button answers calls.
        mHomeAnswerCall = (SwitchPreference) findPreference(KEY_HOME_ANSWER_CALL);

        Action defaultHomeLongPressAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_longPressOnHomeBehavior));
        Action defaultHomeDoubleTapAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_doubleTapOnHomeBehavior));
        Action homeLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                defaultHomeLongPressAction);
        Action homeDoubleTapAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                defaultHomeDoubleTapAction);

        Action defaultBackLongPressAction = Action.fromIntSafe(res.getInteger(
                org.lineageos.platform.internal.R.integer.config_longPressOnBackBehavior));
        Action backLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_BACK_LONG_PRESS_ACTION,
                defaultBackLongPressAction);

        backlight = (ButtonBacklightBrightness) findPreference(KEY_BUTTON_BACKLIGHT);
        if (!backlight.isButtonSupported(getActivity())
                && !backlight.isKeyboardSupported(getActivity())) {
            prefScreen.removePreference(backlight);
            backlight = null;
        } else if (mLineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE)) {
            backlight.setEnabled(!(Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 0,
                    UserHandle.USER_CURRENT) == 1));
        }

        if (!hasHomeKey && !hasBackKey && !hasMenuKey && !hasAssistKey && !hasAppSwitchKey) {
            prefScreen.removePreference(mAnbi);
            mAnbi = null;
        } else if (mLineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE)) {
            mAnbi.setEnabled(!(Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.HARDWARE_KEYS_DISABLE, 0,
                    UserHandle.USER_CURRENT) == 1));
        }

        if (hasPowerKey) {
            if (!TelephonyUtils.isVoiceCapable(getActivity())) {
                powerCategory.removePreference(mPowerEndCall);
                mPowerEndCall = null;
            }
            if (!DeviceUtils.deviceSupportsFlashLight(getActivity())) {
                powerCategory.removePreference(mTorchLongPressPowerGesture);
                powerCategory.removePreference(mTorchLongPressPowerTimeout);
            }
        } else {
            prefScreen.removePreference(powerCategory);
        }

        if (hasHomeKey) {
            if (!showHomeWake) {
                homeCategory.removePreference(findPreference(KEY_HOME_WAKE_SCREEN));
            }

            if (!TelephonyUtils.isVoiceCapable(getActivity())) {
                homeCategory.removePreference(mHomeAnswerCall);
                mHomeAnswerCall = null;
            }

            mHomeLongPressAction = initList(KEY_HOME_LONG_PRESS, homeLongPressAction);
            mHomeDoubleTapAction = initList(KEY_HOME_DOUBLE_TAP, homeDoubleTapAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(homeCategory);
        }

        if (hasBackKey) {
            if (!showBackWake) {
                backCategory.removePreference(findPreference(KEY_BACK_WAKE_SCREEN));
            }

            mBackLongPressAction = initList(KEY_BACK_LONG_PRESS, backLongPressAction);
        } else {
            prefScreen.removePreference(backCategory);
        }

        if (hasMenuKey) {
            if (!showMenuWake) {
                menuCategory.removePreference(findPreference(KEY_MENU_WAKE_SCREEN));
            }

            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_MENU_ACTION, Action.MENU);
            mMenuPressAction = initList(KEY_MENU_PRESS, pressAction);

            Action longPressAction = Action.fromSettings(resolver,
                        LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION,
                        hasAssistKey ? Action.NOTHING : Action.APP_SWITCH);
            mMenuLongPressAction = initList(KEY_MENU_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(menuCategory);
        }

        if (hasAssistKey) {
            if (!showAssistWake) {
                assistCategory.removePreference(findPreference(KEY_ASSIST_WAKE_SCREEN));
            }

            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_ASSIST_ACTION, Action.SEARCH);
            mAssistPressAction = initList(KEY_ASSIST_PRESS, pressAction);

            Action longPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION, Action.VOICE_SEARCH);
            mAssistLongPressAction = initList(KEY_ASSIST_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(assistCategory);
        }

        if (hasAppSwitchKey) {
            if (!showAppSwitchWake) {
                appSwitchCategory.removePreference(findPreference(KEY_APP_SWITCH_WAKE_SCREEN));
            }

            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_APP_SWITCH_ACTION, Action.APP_SWITCH);
            mAppSwitchPressAction = initList(KEY_APP_SWITCH_PRESS, pressAction);

            Action longPressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION, Action.SPLIT_SCREEN);
            mAppSwitchLongPressAction = initList(KEY_APP_SWITCH_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(appSwitchCategory);
        }

        if (hasCameraKey) {
            mCameraWakeScreen = (SwitchPreference) findPreference(KEY_CAMERA_WAKE_SCREEN);
            mCameraSleepOnRelease =
                    (SwitchPreference) findPreference(KEY_CAMERA_SLEEP_ON_RELEASE);
            mCameraLaunch = (SwitchPreference) findPreference(KEY_CAMERA_LAUNCH);

            if (!showCameraWake) {
                prefScreen.removePreference(mCameraWakeScreen);
            }
            // Only show 'Camera sleep on release' if the device has a focus key
            if (res.getBoolean(org.lineageos.platform.internal.R.bool.config_singleStageCameraKey)) {
                prefScreen.removePreference(mCameraSleepOnRelease);
            }
        } else {
            prefScreen.removePreference(cameraCategory);
        }

        if (hasVolumeKeys) {
            if (!showVolumeWake) {
                volumeCategory.removePreference(findPreference(KEY_VOLUME_WAKE_SCREEN));
            }

            if (!TelephonyUtils.isVoiceCapable(getActivity())) {
                volumeCategory.removePreference(findPreference(KEY_VOLUME_ANSWER_CALL));
            }

            int cursorControlAction = Settings.System.getIntForUser(resolver,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0, UserHandle.USER_CURRENT);
            mVolumeKeyCursorControl = initList(KEY_VOLUME_KEY_CURSOR_CONTROL,
                    cursorControlAction);

            int swapVolumeKeys = LineageSettings.System.getIntForUser(getContentResolver(),
                    LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, 0, UserHandle.USER_CURRENT);
            mSwapVolumeButtons = (SwitchPreference)
                    prefScreen.findPreference(KEY_SWAP_VOLUME_BUTTONS);
            if (mSwapVolumeButtons != null) {
                mSwapVolumeButtons.setChecked(swapVolumeKeys > 0);
            }

            mVolumeWakeScreen = (SwitchPreference) findPreference(LineageSettings.System.VOLUME_WAKE_SCREEN);
            mVolumeMusicControls = (SwitchPreference) findPreference(KEY_VOLUME_MUSIC_CONTROLS);

            if (mVolumeWakeScreen != null) {
                if (mVolumeMusicControls != null) {
                    mVolumeMusicControls.setDependency(KEY_VOLUME_WAKE_SCREEN);
                    mVolumeWakeScreen.setDisableDependentsState(true);
                }
            }

            mScreenshotDelay = (CustomSeekBarPreference) findPreference(KEY_SCREENSHOT_DELAY);
            int delay = (int) ViewConfiguration.get(getActivity()).getScreenshotChordKeyTimeout();
            mScreenshotDelay.setDefaultValue(delay);
        } else {
            prefScreen.removePreference(volumeCategory);
        }

        if (mCameraWakeScreen != null) {
            if (mCameraSleepOnRelease != null && !getResources().getBoolean(
                    org.lineageos.platform.internal.R.bool.config_singleStageCameraKey)) {
                mCameraSleepOnRelease.setDependency(KEY_CAMERA_WAKE_SCREEN);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Power button ends calls.
        if (mPowerEndCall != null) {
            final int incallPowerBehavior = Settings.Secure.getIntForUser(getContentResolver(),
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT, UserHandle.USER_CURRENT);
            final boolean powerButtonEndsCall =
                    (incallPowerBehavior == Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP);
            mPowerEndCall.setChecked(powerButtonEndsCall);
        }

        // Home button answers calls.
        if (mHomeAnswerCall != null) {
            final int incallHomeBehavior = LineageSettings.Secure.getIntForUser(getContentResolver(),
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DEFAULT, UserHandle.USER_CURRENT);
            final boolean homeButtonAnswersCall =
                (incallHomeBehavior == LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER);
            mHomeAnswerCall.setChecked(homeButtonAnswersCall);
        }
    }

    private ListPreference initList(String key, Action value) {
        return initList(key, value.ordinal());
    }

    private ListPreference initList(String key, int value) {
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        if (list == null) return null;
        list.setValue(Integer.toString(value));
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private void handleListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        LineageSettings.System.putIntForUser(getContentResolver(), setting, Integer.valueOf(value), UserHandle.USER_CURRENT);
    }

    private void handleSystemListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        Settings.System.putIntForUser(getContentResolver(), setting, Integer.valueOf(value), UserHandle.USER_CURRENT);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHardwareKeysDisable) {
            boolean value = (Boolean) newValue;
            if (mAnbi != null) {
                mAnbi.setEnabled(!value);
            }
            if (backlight != null) {
                backlight.setEnabled(!value);
            }
            return true;
        } else if (preference == mHomeLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mHomeDoubleTapAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mBackLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_BACK_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mMenuPressAction) {
            handleListChange(mMenuPressAction, newValue,
                    LineageSettings.System.KEY_MENU_ACTION);
            return true;
        } else if (preference == mMenuLongPressAction) {
            handleListChange(mMenuLongPressAction, newValue,
                    LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAssistPressAction) {
            handleListChange(mAssistPressAction, newValue,
                    LineageSettings.System.KEY_ASSIST_ACTION);
            return true;
        } else if (preference == mAssistLongPressAction) {
            handleListChange(mAssistLongPressAction, newValue,
                    LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAppSwitchPressAction) {
            handleListChange(mAppSwitchPressAction, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_ACTION);
            return true;
        } else if (preference == mAppSwitchLongPressAction) {
            handleListChange(mAppSwitchLongPressAction, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mVolumeKeyCursorControl) {
            handleSystemListChange(mVolumeKeyCursorControl, newValue,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL);
            return true;
        } else if (preference == mTorchLongPressPowerTimeout) {
            handleListChange(mTorchLongPressPowerTimeout, newValue,
                    LineageSettings.System.TORCH_LONG_PRESS_POWER_TIMEOUT);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mSwapVolumeButtons) {
            int value = mSwapVolumeButtons.isChecked()
                    ? (ScreenType.isTablet(getActivity()) ? 2 : 1) : 0;
            if (value == 2) {
                Display defaultDisplay = getActivity().getWindowManager().getDefaultDisplay();

                DisplayInfo displayInfo = new DisplayInfo();
                defaultDisplay.getDisplayInfo(displayInfo);

                // Not all tablets are landscape
                if (displayInfo.getNaturalWidth() < displayInfo.getNaturalHeight()) {
                    value = 1;
                }
            }
            LineageSettings.System.putIntForUser(getActivity().getContentResolver(),
                    LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, value, UserHandle.USER_CURRENT);
        } else if (preference == mPowerEndCall) {
            handleTogglePowerButtonEndsCallPreferenceClick();
            return true;
        } else if (preference == mHomeAnswerCall) {
            handleToggleHomeButtonAnswersCallPreferenceClick();
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void handleTogglePowerButtonEndsCallPreferenceClick() {
        Settings.Secure.putIntForUser(getContentResolver(),
                Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR, (mPowerEndCall.isChecked()
                        ? Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP
                        : Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF), UserHandle.USER_CURRENT);
    }

    private void handleToggleHomeButtonAnswersCallPreferenceClick() {
        LineageSettings.Secure.putIntForUser(getContentResolver(),
                LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR, (mHomeAnswerCall.isChecked()
                        ? LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER
                        : LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DO_NOTHING), UserHandle.USER_CURRENT);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.ADVANCED_REBOOT, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.HARDWARE_KEYS_DISABLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ANBI_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREENSHOT_TYPE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREENSHOT_DELAY,
                (int) ViewConfiguration.get(mContext).getScreenshotChordKeyTimeout(), UserHandle.USER_CURRENT);
        ButtonBacklightBrightness.reset(mContext);
        PowerMenuActions.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.crdroid_settings_button;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    final boolean hasPowerKey = DeviceUtils.hasPowerKey();
                    final boolean hasHomeKey = DeviceUtils.hasHomeKey(context);
                    final boolean hasBackKey = DeviceUtils.hasBackKey(context);
                    final boolean hasMenuKey = DeviceUtils.hasMenuKey(context);
                    final boolean hasAssistKey = DeviceUtils.hasAssistKey(context);
                    final boolean hasAppSwitchKey = DeviceUtils.hasAppSwitchKey(context);
                    final boolean hasCameraKey = DeviceUtils.hasCameraKey(context);
                    final boolean hasVolumeKeys = DeviceUtils.hasVolumeKeys(context);

                    LineageHardwareManager mLineageHardware = LineageHardwareManager.getInstance(context);

                    if (!mLineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE))
                        keys.add(HWKEYS_DISABLED);

                    if (!hasHomeKey && !hasBackKey && !hasMenuKey && !hasAssistKey && !hasAppSwitchKey)
                        keys.add(KEY_ANBI);

                    if (!hasPowerKey) {
                        keys.add(KEY_POWER_MENU);
                        keys.add(KEY_POWER_END_CALL);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                    } else {
                        if (!TelephonyUtils.isVoiceCapable(context)) {
                            keys.add(KEY_POWER_END_CALL);
                        }
                        if (!DeviceUtils.deviceSupportsFlashLight(context)) {
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                        }
                    }

                    keys.add(KEY_HOME_WAKE_SCREEN);
                    keys.add(KEY_HOME_LONG_PRESS);
                    keys.add(KEY_HOME_DOUBLE_TAP);

                    if (!hasHomeKey || !TelephonyUtils.isVoiceCapable(context)) {
                        keys.add(KEY_HOME_ANSWER_CALL);
                    }

                    keys.add(KEY_BACK_WAKE_SCREEN);
                    keys.add(KEY_BACK_LONG_PRESS);
                    keys.add(KEY_MENU_WAKE_SCREEN);
                    keys.add(KEY_MENU_PRESS);
                    keys.add(KEY_MENU_LONG_PRESS);
                    keys.add(KEY_ASSIST_WAKE_SCREEN);
                    keys.add(KEY_ASSIST_PRESS);
                    keys.add(KEY_ASSIST_LONG_PRESS);
                    keys.add(KEY_APP_SWITCH_WAKE_SCREEN);
                    keys.add(KEY_APP_SWITCH_PRESS);
                    keys.add(KEY_APP_SWITCH_LONG_PRESS);
                    keys.add(KEY_CAMERA_WAKE_SCREEN);

                    if (!hasCameraKey) {
                        keys.add(KEY_CAMERA_SLEEP_ON_RELEASE);
                        keys.add(KEY_CAMERA_LAUNCH);
                    }

                    keys.add(KEY_VOLUME_WAKE_SCREEN);

                    if (!hasVolumeKeys) {
                        keys.add(KEY_VOLUME_ANSWER_CALL);
                        keys.add(KEY_VOLUME_MUSIC_CONTROLS);
                        keys.add(KEY_VOLUME_KEY_CURSOR_CONTROL);
                        keys.add(KEY_SWAP_VOLUME_BUTTONS);
                    } else {
                        if (!TelephonyUtils.isVoiceCapable(context)) {
                            keys.add(KEY_VOLUME_ANSWER_CALL);
                        }
                    }

                    if (!ButtonBacklightBrightness.isButtonSupported(context)
                            && !ButtonBacklightBrightness.isKeyboardSupported(context)) {
                        keys.add(KEY_BUTTON_BACKLIGHT);
                    }

                    keys.add(KEY_ADDITIONAL_BUTTONS);

                    return keys;
                }
            };
}
