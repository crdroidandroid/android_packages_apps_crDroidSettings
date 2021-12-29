/*
 * Copyright (C) 2022 crDroid Android Project
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

package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import androidx.preference.PreferenceViewHolder;
import android.view.ViewGroup.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;
import android.net.Uri;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.Indexable;
import com.android.settings.SettingsPreferenceFragment;

import com.bumptech.glide.Glide;

import com.android.internal.util.crdroid.ThemeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.json.JSONObject;
import org.json.JSONException;

public class StatusbarIcons extends SettingsPreferenceFragment {

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private String mCategory = "android.theme.customization.icon_pack.android";

    private List<String> mPkgs;

    Map<String, String> overlayMap = new HashMap<String, String>();
    {
        overlayMap.put("com.android.settings", "android.theme.customization.icon_pack.settings");
        overlayMap.put("com.android.systemui", "android.theme.customization.icon_pack.systemui");
        overlayMap.put("com.android.launcher3", "android.theme.customization.icon_pack.launcher");
        overlayMap.put("com.android.wallpaper", "android.theme.customization.icon_pack.themepicker");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.theme_customization_icon_pack_title);

        mThemeUtils = new ThemeUtils(getActivity());
        mPkgs = mThemeUtils.getOverlayPackagesForCategory(mCategory, "android");
        Collections.sort(mPkgs);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.item_view, container, false);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 3);
        mRecyclerView.setLayoutManager(gridLayoutManager);
        Adapter mAdapter = new Adapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        Context context;
        String mSelectedPkg;
        String mAppliedPkg;

        public Adapter(Context context) {
            this.context = context;
        }

        @Override
        public CustomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.icon_option, parent, false);
            CustomViewHolder vh = new CustomViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(CustomViewHolder holder, final int position) {
            String iconPkg = mPkgs.get(position);

            holder.image1.setBackgroundDrawable(getDrawable(holder.image1.getContext(), iconPkg, "ic_wifi_signal_4"));
            holder.image2.setBackgroundDrawable(getDrawable(holder.image2.getContext(), iconPkg, "ic_signal_cellular_4_4_bar"));
            holder.image3.setBackgroundDrawable(getDrawable(holder.image3.getContext(), iconPkg, "ic_qs_airplane"));
            holder.image4.setBackgroundDrawable(getDrawable(holder.image4.getContext(), iconPkg, "ic_qs_flashlight"));

            String currentPackageName = mThemeUtils.getOverlayInfos(mCategory).stream()
                .filter(info -> info.isEnabled())
                .map(info -> info.packageName)
                .findFirst()
                .orElse("android");

            holder.name.setText("android".equals(iconPkg) ? "Default" : getLabel(holder.name.getContext(), iconPkg));

            if (currentPackageName.equals(iconPkg)) {
                mAppliedPkg = iconPkg;
                if (mSelectedPkg == null) {
                    mSelectedPkg = iconPkg;
                }
            }

            holder.itemView.setActivated(iconPkg == mSelectedPkg);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateActivatedStatus(mSelectedPkg, false);
                    updateActivatedStatus(iconPkg, true);
                    mSelectedPkg = iconPkg;
                    enableOverlays(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPkgs.size();
        }

        public class CustomViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image1;
            ImageView image2;
            ImageView image3;
            ImageView image4;
            public CustomViewHolder(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.option_label);
                image1 = (ImageView) itemView.findViewById(R.id.image1);
                image2 = (ImageView) itemView.findViewById(R.id.image2);
                image3 = (ImageView) itemView.findViewById(R.id.image3);
                image4 = (ImageView) itemView.findViewById(R.id.image4);
            }
        }

        private void updateActivatedStatus(String pkg, boolean isActivated) {
            int index = mPkgs.indexOf(pkg);
            if (index < 0) {
                return;
            }
            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.itemView != null) {
                holder.itemView.setActivated(isActivated);
            }
        }
    }

    public Drawable getDrawable(Context context, String pkg, String drawableName) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pkg.equals("android") ? Resources.getSystem()
                    : pm.getResourcesForApplication(pkg);
            int resId = res.getIdentifier(drawableName, "drawable", pkg);
            if (resId == 0) {
                return Resources.getSystem().getDrawable(
                        Resources.getSystem().getIdentifier(drawableName, "drawable", "android"));
            }
            return res.getDrawable(resId);
        }
        catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getLabel(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0)
                    .loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pkg;
    }

    public void enableOverlays(int position) {
        mThemeUtils.setOverlayEnabled(mCategory, mPkgs.get(position));
        String pattern = "android".equals(mPkgs.get(position)) ? ""
                : mPkgs.get(position).split("\\.")[4];
        for (Map.Entry<String, String> entry : overlayMap.entrySet()) {
            enableOverlay(entry.getValue(), entry.getKey(), pattern);
        }
    }

    public void enableOverlay(String category, String target, String pattern) {
        if (pattern.isEmpty()) {
            mThemeUtils.setOverlayEnabled(category, "android");
            return;
        }
        for (String pkg: mThemeUtils.getOverlayPackagesForCategory(category, target)) {
            if (pkg.contains(pattern)) {
                mThemeUtils.setOverlayEnabled(category, pkg);
            }
        }
    }
}
