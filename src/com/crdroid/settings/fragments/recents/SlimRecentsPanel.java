/*
 * Copyright (C) 2015-2017 SlimRoms Project
 * Copyright (C) 2017 The ABC rom
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

package com.crdroid.settings.fragments.recents;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
//import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
//import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
//import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class SlimRecentsPanel extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, DialogInterface.OnDismissListener {

    private static final String TAG = "SlimRecentsPanelSettings";

    // Preferences
    private static final String RECENTS_ICON_PACK = "slim_icon_pack";
    private static final String RECENTS_MAX_APPS = "recents_max_apps";
    private static final String RECENT_PANEL_GRAVITY =
            "recent_panel_gravity";
    private static final String RECENT_PANEL_SCALE =
            "recent_panel_scale";
    private static final String RECENT_PANEL_EXPANDED_MODE =
            "recent_panel_expanded_mode";
    private static final String RECENT_PANEL_BG_COLOR =
            "recent_panel_bg_color";
    private static final String RECENT_CARD_BG_COLOR =
            "recent_card_bg_color";
    private static final String RECENT_PANEL_CORNER_RADIUS =
            "slim_recents_corner_radius";

    private final static String[] sSupportedActions = new String[] {
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme"
    };

    private static final String[] sSupportedCategories = new String[] {
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    };

    private static final int MENU_RESET = Menu.FIRST;
    private static final int DIALOG_RESET_CONFIRM = 1;

    private Preference mRecentsIconPack;
    private CustomSeekBarPreference mMaxApps;
    private SwitchPreference mRecentPanelGravity;
    private CustomSeekBarPreference mRecentPanelScale;
    private ListPreference mRecentPanelExpandedMode;
    private ColorPickerPreference mRecentPanelBgColor;
    private ColorPickerPreference mRecentCardBgColor;
    private SwitchPreference mRecentPanelRadius;

    private AlertDialog mDialog;
    private ListView mListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.slim_recents_panel_settings);
        initializeAllPreferences();

        setHasOptionsMenu(true);

        mFooterPreferenceMixin.createFooterPreference().setTitle(R.string.slim_recents_hints_footer);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_RESET_CONFIRM: {
                Dialog dialog;
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                alertDialog.setTitle(R.string.recent_reset_title);
                alertDialog.setMessage(R.string.recent_reset_confirm);
                alertDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        reset(getContext());
                        updateRecentPanelPreferences();
                    }
                });
                alertDialog.setNegativeButton(R.string.write_settings_off, null);
                dialog = alertDialog.create();
                return dialog;
            }
         }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DIALOG_RESET_CONFIRM:
                return MetricsEvent.CRDROID_SETTINGS;
            default:
                return 0;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(com.android.internal.R.drawable.ic_menu_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                showDialog(DIALOG_RESET_CONFIRM);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENTS_MAX_APPS, 15, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_SCALE_FACTOR, 115, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_EXPANDED_MODE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_GRAVITY, Gravity.END, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SLIM_RECENTS_CORNER_RADIUS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_BG_COLOR, 0x763367d6, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_CARD_BG_COLOR, 0x00ffffff, UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(resolver,
                Settings.System.SLIM_RECENTS_ICON_PACK, "", UserHandle.USER_CURRENT);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mRecentPanelGravity) {
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    Settings.System.RECENT_PANEL_GRAVITY,
                    ((Boolean) newValue) ? Gravity.START : Gravity.END, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mRecentPanelScale) {
            Settings.System.putIntForUser(getContext().getContentResolver(),
                Settings.System.RECENT_PANEL_SCALE_FACTOR,
                Integer.valueOf(String.valueOf(newValue)), UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mRecentPanelExpandedMode) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    Settings.System.RECENT_PANEL_EXPANDED_MODE, value, UserHandle.USER_CURRENT);
            int index = mRecentPanelExpandedMode.findIndexOfValue((String) newValue);
            mRecentPanelExpandedMode.setSummary(
                    mRecentPanelExpandedMode.getEntries()[index]);
            return true;
        } else if (preference == mRecentPanelBgColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            if (hex.equals("#763367d6")) {
                preference.setSummary(R.string.default_string);
            } else {
                preference.setSummary(hex);
            }
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    Settings.System.RECENT_PANEL_BG_COLOR,
                    intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mRecentCardBgColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            if (hex.equals("#00ffffff")) {
                preference.setSummary(R.string.default_auto_string);
            } else {
                preference.setSummary(hex);
            }
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    Settings.System.RECENT_CARD_BG_COLOR,
                    intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mMaxApps) {
            Settings.System.putIntForUser(getContext().getContentResolver(),
                Settings.System.RECENTS_MAX_APPS, Integer.valueOf(String.valueOf(newValue)), UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    private void updateRecentPanelPreferences() {
        ContentResolver resolver = getActivity().getContentResolver();

        String currentIconPack =  Settings.System.getStringForUser(resolver,
            Settings.System.SLIM_RECENTS_ICON_PACK, UserHandle.USER_CURRENT);

        if (currentIconPack != null && !currentIconPack.isEmpty()) {
            mRecentsIconPack.setSummary(currentIconPack);
        } else {
            mRecentsIconPack.setSummary(R.string.recents_icon_pack_summary);
        }

        boolean recentLeftyMode = Settings.System.getIntForUser(resolver,
                Settings.System.RECENT_PANEL_GRAVITY, Gravity.END, UserHandle.USER_CURRENT) == Gravity.START;
        mRecentPanelGravity.setChecked(recentLeftyMode);

        boolean recentCornerRadius = Settings.System.getIntForUser(resolver,
                Settings.System.SLIM_RECENTS_CORNER_RADIUS, 1, UserHandle.USER_CURRENT) != 0;
        mRecentPanelRadius.setChecked(recentCornerRadius);

        int maxapps = Settings.System.getIntForUser(resolver,
                Settings.System.RECENTS_MAX_APPS, 15, UserHandle.USER_CURRENT);
        mMaxApps.setValue(maxapps);

        int recentScale = Settings.System.getIntForUser(resolver,
                Settings.System.RECENT_PANEL_SCALE_FACTOR, 115, UserHandle.USER_CURRENT);
        mRecentPanelScale.setValue(recentScale);

        int recentExpandedMode = Settings.System.getIntForUser(resolver,
                Settings.System.RECENT_PANEL_EXPANDED_MODE, 1, UserHandle.USER_CURRENT);
        mRecentPanelExpandedMode.setValue(recentExpandedMode + "");
        mRecentPanelExpandedMode.setSummary(mRecentPanelExpandedMode.getEntry());

        int intColor = Settings.System.getIntForUser(resolver,
                Settings.System.RECENT_PANEL_BG_COLOR, 0x763367d6, UserHandle.USER_CURRENT);
        String hexColor = String.format("#%08x", (0x00ffffff & intColor));
        if (hexColor.equals("#763367d6")) {
            mRecentPanelBgColor.setSummary(R.string.default_string);
        } else {
            mRecentPanelBgColor.setSummary(hexColor);
        }
        mRecentPanelBgColor.setNewPreviewColor(intColor);

        int intColorCard = Settings.System.getIntForUser(resolver,
                Settings.System.RECENT_CARD_BG_COLOR, 0x00ffffff, UserHandle.USER_CURRENT);
        String hexColorCard = String.format("#%08x", (0x00ffffff & intColorCard));
        if (hexColorCard.equals("#00ffffff")) {
            mRecentCardBgColor.setSummary(R.string.default_auto_string);
        } else {
            mRecentCardBgColor.setSummary(hexColorCard);
        }
        mRecentCardBgColor.setNewPreviewColor(intColorCard);
    }

    private void initializeAllPreferences() {
        mRecentsIconPack = (Preference) findPreference(RECENTS_ICON_PACK);

        mRecentPanelGravity =
                (SwitchPreference) findPreference(RECENT_PANEL_GRAVITY);
        mRecentPanelGravity.setOnPreferenceChangeListener(this);

        mMaxApps = (CustomSeekBarPreference) findPreference(RECENTS_MAX_APPS);
        mMaxApps.setOnPreferenceChangeListener(this);

        // Recent panel background color
        mRecentPanelBgColor =
                (ColorPickerPreference) findPreference(RECENT_PANEL_BG_COLOR);
        mRecentPanelBgColor.setOnPreferenceChangeListener(this);

        // Recent card background color
        mRecentCardBgColor =
                (ColorPickerPreference) findPreference(RECENT_CARD_BG_COLOR);
        mRecentCardBgColor.setOnPreferenceChangeListener(this);

        mRecentPanelScale =
                (CustomSeekBarPreference) findPreference(RECENT_PANEL_SCALE);
        mRecentPanelScale.setOnPreferenceChangeListener(this);

        mRecentPanelExpandedMode =
                (ListPreference) findPreference(RECENT_PANEL_EXPANDED_MODE);
        mRecentPanelExpandedMode.setOnPreferenceChangeListener(this);

        mRecentPanelRadius =
                (SwitchPreference) findPreference(RECENT_PANEL_CORNER_RADIUS);

        updateRecentPanelPreferences();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mRecentsIconPack) {
            pickIconPack(getContext());
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    /** Slim Recents Icon Pack Dialog **/
    private void pickIconPack(final Context context) {
        if (mDialog != null) {
            return;
        }
        Map<String, IconPackInfo> supportedPackages = getSupportedPackages(context);
        if (supportedPackages.isEmpty()) {
            Toast.makeText(context, R.string.no_iconpacks_summary, Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
        .setTitle(R.string.dialog_pick_iconpack_title)
        .setOnDismissListener(this)
        .setNegativeButton(R.string.cancel, null)
        .setView(createDialogView(context, supportedPackages));
        mDialog = builder.show();
    }

    private View createDialogView(final Context context, Map<String, IconPackInfo> supportedPackages) {
        final LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.dialog_iconpack, null);
        final IconAdapter adapter = new IconAdapter(context, supportedPackages);

        mListView = (ListView) view.findViewById(R.id.iconpack_list);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                        int position, long id) {
                if (adapter.isCurrentIconPack(position)) {
                    return;
                }
                String selectedPackage = adapter.getItem(position);
                Settings.System.putStringForUser(getContext().getContentResolver(),
                        Settings.System.SLIM_RECENTS_ICON_PACK, selectedPackage, UserHandle.USER_CURRENT);
                mDialog.dismiss();
            }
        });

        return view;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mDialog != null) {
            mDialog = null;
        }
        updateRecentPanelPreferences();
    }

    private static class IconAdapter extends BaseAdapter {
        ArrayList<IconPackInfo> mSupportedPackages;
        LayoutInflater mLayoutInflater;
        String mCurrentIconPack;
        int mCurrentIconPackPosition = -1;

        IconAdapter(Context ctx, Map<String, IconPackInfo> supportedPackages) {
            mLayoutInflater = LayoutInflater.from(ctx);
            mSupportedPackages = new ArrayList<IconPackInfo>(supportedPackages.values());
            Collections.sort(mSupportedPackages, new Comparator<IconPackInfo>() {
                @Override
                public int compare(IconPackInfo lhs, IconPackInfo rhs) {
                    return lhs.label.toString().compareToIgnoreCase(rhs.label.toString());
                }
            });

            Resources res = ctx.getResources();
            String defaultLabel = res.getString(R.string.default_iconpack_title);
            Drawable icon = res.getDrawable(android.R.drawable.sym_def_app_icon);
            mSupportedPackages.add(0, new IconPackInfo(defaultLabel, icon, ""));
            mCurrentIconPack = Settings.System.getStringForUser(ctx.getContentResolver(),
                Settings.System.SLIM_RECENTS_ICON_PACK, UserHandle.USER_CURRENT);
        }

        @Override
        public int getCount() {
            return mSupportedPackages.size();
        }

        @Override
        public String getItem(int position) {
            return (String) mSupportedPackages.get(position).packageName;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        public boolean isCurrentIconPack(int position) {
            return mCurrentIconPackPosition == position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.iconpack_view_radio, null);
            }
            IconPackInfo info = mSupportedPackages.get(position);
            TextView txtView = (TextView) convertView.findViewById(R.id.title);
            txtView.setText(info.label);
            ImageView imgView = (ImageView) convertView.findViewById(R.id.icon);
            imgView.setImageDrawable(info.icon);
            RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
            boolean isCurrentIconPack = info.packageName.equals(mCurrentIconPack);
            radioButton.setChecked(isCurrentIconPack);
            if (isCurrentIconPack) {
                mCurrentIconPackPosition = position;
            }
            return convertView;
        }
    }

    private Map<String, IconPackInfo> getSupportedPackages(Context context) {
        Intent i = new Intent();
        Map<String, IconPackInfo> packages = new HashMap<String, IconPackInfo>();
        PackageManager packageManager = context.getPackageManager();
        for (String action : sSupportedActions) {
            i.setAction(action);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                IconPackInfo info = new IconPackInfo(r, packageManager);
                packages.put(r.activityInfo.packageName, info);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        for (String category : sSupportedCategories) {
            i.addCategory(category);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                IconPackInfo info = new IconPackInfo(r, packageManager);
                packages.put(r.activityInfo.packageName, info);
            }
            i.removeCategory(category);
        }
        return packages;
    }

    static class IconPackInfo {
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            icon = r.loadIcon(packageManager);
            label = r.loadLabel(packageManager);
        }

        IconPackInfo(){
        }

        public IconPackInfo(String label, Drawable icon, String packageName) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
        }
    }
}
