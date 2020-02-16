/*
 * Copyright (C) 2016-2019 crDroid Android Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.internal.util.omni.OmniSwitchConstants;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SearchIndexable
public class Recents extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "Recents";

    private static final String PREF_RECENTS_INFO = "recents_info";
    private static final String PREF_RECENTS_STYLE = "recents_component";
    //private static final String PREF_RECENTS_ICONPACK = "recents_icon_pack";
    private static final String CATEGORY_STOCK = "stock_recents";
    private static final String CATEGORY_OMNI = "omni_recents";

    private final static String[] sSupportedActions = new String[] {
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme"
    };

    private static final String[] sSupportedCategories = new String[] {
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    };

    private AlertDialog mDialog;
    private ListView mListView;

    private ListPreference mRecentsStyle;
    private Preference mRecentsInfo;
    //private Preference mIconSettings;
    private Preference mStockSettings;
    private Preference mOmniSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_recents);

        final PreferenceScreen prefScreen = getPreferenceScreen();

        int navbarMode = Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.NAVIGATION_MODE, 3, UserHandle.USER_CURRENT);

        int recentsStyle = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.RECENTS_COMPONENT, 0, UserHandle.USER_CURRENT);

        mRecentsInfo = (Preference) findPreference(PREF_RECENTS_INFO);
        mRecentsStyle = (ListPreference) findPreference(PREF_RECENTS_STYLE);
        mRecentsStyle.setOnPreferenceChangeListener(this);
        //mIconSettings = (Preference) findPreference(PREF_RECENTS_ICONPACK);
        //mStockSettings = (PreferenceCategory) findPreference(CATEGORY_STOCK);
        mOmniSettings = (PreferenceCategory) findPreference(CATEGORY_OMNI);

        mRecentsInfo.setEnabled(true);

        if (navbarMode == 0) {
            mRecentsStyle.setEnabled(true);
            mRecentsStyle.setSelectable(true);
            //mIconSettings.setEnabled(true);
            //mIconSettings.setSelectable(true);

            switch (recentsStyle) {
                case 0:
                    //mStockSettings.setEnabled(false);
                    //mStockSettings.setSelectable(false);
                    mOmniSettings.setEnabled(false);
                    mOmniSettings.setSelectable(false);
                    break;
                case 1:
                    //mStockSettings.setEnabled(true);
                    //mStockSettings.setSelectable(true);
                    mOmniSettings.setEnabled(false);
                    mOmniSettings.setSelectable(false);
                    break;
                case 2:
                    boolean SwitchRunning = OmniSwitchConstants.isOmniSwitchRunning(getContext());
                    if (!SwitchRunning) {
                        Toast.makeText(getActivity(), R.string.omniswitch_first_time_message,
                            Toast.LENGTH_LONG).show();
                        Settings.System.putInt(getContentResolver(),
                            Settings.System.RECENTS_OMNI_SWITCH_ENABLED, 0);
                    }
                    //mStockSettings.setEnabled(false);
                    //mStockSettings.setSelectable(false);
                    mOmniSettings.setEnabled(true);
                    mOmniSettings.setSelectable(true);
                    break;
                default:
            }
        } else {
            mRecentsStyle.setEnabled(false);
            mRecentsStyle.setSelectable(false);
            //mIconSettings.setEnabled(false);
            //mIconSettings.setSelectable(false);
            //mStockSettings.setEnabled(false);
            //mStockSettings.setSelectable(false);
            mOmniSettings.setEnabled(false);
            mOmniSettings.setSelectable(false);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mRecentsStyle) {
            int recentsStyle = Integer.parseInt((String) newValue);
            updateRecentStyleSettings(recentsStyle);
            return true;
        }
        return false;
    }

    private void updateRecentStyleSettings(int recentsStyle) {
        switch (recentsStyle) {
            case 0:
                //mStockSettings.setEnabled(false);
                //mStockSettings.setSelectable(false);
                mOmniSettings.setEnabled(false);
                mOmniSettings.setSelectable(false);
                Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENTS_OMNI_SWITCH_ENABLED, 0);
                //Toast.makeText(getActivity(), R.string.device_restart_required, Toast.LENGTH_SHORT).show();
                break;
            case 1:
                //mStockSettings.setEnabled(true);
                //mStockSettings.setSelectable(true);
                mOmniSettings.setEnabled(false);
                mOmniSettings.setSelectable(false);
                Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENTS_OMNI_SWITCH_ENABLED, 0);
                //Toast.makeText(getActivity(), R.string.device_restart_required, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                boolean SwitchRunning = OmniSwitchConstants.isOmniSwitchRunning(getContext());
                if (!SwitchRunning) {
                    Toast.makeText(getActivity(), R.string.omniswitch_first_time_message,
                        Toast.LENGTH_LONG).show();
                    startActivity(OmniSwitchConstants.INTENT_LAUNCH_APP);
                }
                //mStockSettings.setEnabled(false);
                //mStockSettings.setSelectable(false);
                mOmniSettings.setEnabled(true);
                mOmniSettings.setSelectable(true);
                Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENTS_OMNI_SWITCH_ENABLED, 1);
                break;
        }
    }

    //@Override
    //public boolean onPreferenceTreeClick(Preference preference) {
    //    if (preference == findPreference(PREF_RECENTS_ICONPACK)) {
    //        pickIconPack(getContext());
    //        return true;
    //    }
    //    return super.onPreferenceTreeClick(preference);
    //}

    /** Recents Icon Pack Dialog **/
    //private void pickIconPack(final Context context) {
    //    if (mDialog != null) {
    //        return;
    //    }
    //    Map<String, IconPackInfo> supportedPackages = getSupportedPackages(context);
    //    if (supportedPackages.isEmpty()) {
    //        Toast.makeText(context, R.string.no_iconpacks_summary, Toast.LENGTH_SHORT).show();
    //        return;
    //    }
    //    AlertDialog.Builder builder = new AlertDialog.Builder(context)
    //    .setTitle(R.string.dialog_pick_iconpack_title)
    //    .setOnDismissListener(this)
    //    .setNegativeButton(R.string.cancel, null)
    //    .setView(createDialogView(context, supportedPackages));
    //    mDialog = builder.show();
    //}

    //private View createDialogView(final Context context, Map<String, IconPackInfo> supportedPackages) {
    //    final LayoutInflater inflater = (LayoutInflater) context
    //            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    //    final View view = inflater.inflate(R.layout.dialog_iconpack, null);
    //    final IconAdapter adapter = new IconAdapter(context, supportedPackages);

    //    mListView = (ListView) view.findViewById(R.id.iconpack_list);
    //    mListView.setAdapter(adapter);
    //    mListView.setOnItemClickListener(new OnItemClickListener() {
    //        @Override
    //        public void onItemClick(AdapterView<?> parent, View view,
    //                    int position, long id) {
    //            if (adapter.isCurrentIconPack(position)) {
    //                return;
    //            }
    //            String selectedPackage = adapter.getItem(position);
    //            Settings.System.putString(getContext().getContentResolver(),
    //                    Settings.System.RECENTS_ICON_PACK, selectedPackage);
    //            mDialog.dismiss();
    //        }
    //    });

    //    return view;
    //}

    //@Override
    //public void onDismiss(DialogInterface dialog) {
    //    if (mDialog != null) {
    //        mDialog = null;
    //    }
    //}

    //private static class IconAdapter extends BaseAdapter {
    //    ArrayList<IconPackInfo> mSupportedPackages;
    //    LayoutInflater mLayoutInflater;
    //    String mCurrentIconPack;
    //    int mCurrentIconPackPosition = -1;

    //    IconAdapter(Context ctx, Map<String, IconPackInfo> supportedPackages) {
    //        mLayoutInflater = LayoutInflater.from(ctx);
    //        mSupportedPackages = new ArrayList<IconPackInfo>(supportedPackages.values());
    //        Collections.sort(mSupportedPackages, new Comparator<IconPackInfo>() {
    //            @Override
    //            public int compare(IconPackInfo lhs, IconPackInfo rhs) {
    //                return lhs.label.toString().compareToIgnoreCase(rhs.label.toString());
    //            }
    //        });

    //        Resources res = ctx.getResources();
    //        String defaultLabel = res.getString(R.string.default_iconpack_title);
    //        Drawable icon = res.getDrawable(android.R.drawable.sym_def_app_icon);
    //        mSupportedPackages.add(0, new IconPackInfo(defaultLabel, icon, ""));
    //        mCurrentIconPack = Settings.System.getString(ctx.getContentResolver(),
    //            Settings.System.RECENTS_ICON_PACK);
    //    }

    //    @Override
    //    public int getCount() {
    //        return mSupportedPackages.size();
    //    }

    //    @Override
    //    public String getItem(int position) {
    //        return (String) mSupportedPackages.get(position).packageName;
    //    }

    //    @Override
    //    public long getItemId(int position) {
    //        return 0;
    //    }

    //    public boolean isCurrentIconPack(int position) {
    //        return mCurrentIconPackPosition == position;
    //    }

    //    @Override
    //    public View getView(int position, View convertView, ViewGroup parent) {
    //        if (convertView == null) {
    //            convertView = mLayoutInflater.inflate(R.layout.iconpack_view_radio, null);
    //        }
    //        IconPackInfo info = mSupportedPackages.get(position);
    //        TextView txtView = (TextView) convertView.findViewById(R.id.title);
    //        txtView.setText(info.label);
    //        ImageView imgView = (ImageView) convertView.findViewById(R.id.icon);
    //        imgView.setImageDrawable(info.icon);
    //        RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
    //        boolean isCurrentIconPack = info.packageName.equals(mCurrentIconPack);
    //        radioButton.setChecked(isCurrentIconPack);
    //        if (isCurrentIconPack) {
    //            mCurrentIconPackPosition = position;
    //        }
    //        return convertView;
    //    }
    //}

    //private Map<String, IconPackInfo> getSupportedPackages(Context context) {
    //    Intent i = new Intent();
    //    Map<String, IconPackInfo> packages = new HashMap<String, IconPackInfo>();
    //    PackageManager packageManager = context.getPackageManager();
    //    for (String action : sSupportedActions) {
    //        i.setAction(action);
    //        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
    //            IconPackInfo info = new IconPackInfo(r, packageManager);
    //            packages.put(r.activityInfo.packageName, info);
    //        }
    //    }
    //    i = new Intent(Intent.ACTION_MAIN);
    //    for (String category : sSupportedCategories) {
    //        i.addCategory(category);
    //        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
    //            IconPackInfo info = new IconPackInfo(r, packageManager);
    //            packages.put(r.activityInfo.packageName, info);
    //        }
    //        i.removeCategory(category);
    //    }
    //    return packages;
    //}

    //static class IconPackInfo {
    //    String packageName;
    //    CharSequence label;
    //    Drawable icon;

    //    IconPackInfo(ResolveInfo r, PackageManager packageManager) {
    //        packageName = r.activityInfo.packageName;
    //        icon = r.loadIcon(packageManager);
    //        label = r.loadLabel(packageManager);
    //    }

    //    IconPackInfo(){
    //    }

    //    public IconPackInfo(String label, Drawable icon, String packageName) {
    //        this.label = label;
    //        this.icon = icon;
    //        this.packageName = packageName;
    //    }
    //}

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
    //    Settings.System.putIntForUser(resolver,
    //            Settings.System.RECENTS_CLEAR_ALL_LOCATION, 3, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENTS_COMPONENT, 0, UserHandle.USER_CURRENT);
    //    Settings.System.putString(resolver,
    //            Settings.System.RECENTS_ICON_PACK, "");
    //    Settings.System.putIntForUser(resolver,
    //            Settings.System.SHOW_CLEAR_ALL_RECENTS, 1, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.crdroid_settings_recents;
                    result.add(sir);

                    return result;
                }
            };
}
