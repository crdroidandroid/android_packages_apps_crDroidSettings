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

import static android.os.UserHandle.USER_SYSTEM;
import static com.android.internal.util.crdroid.ThemeUtils.FONT_KEY;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
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
import java.util.List;
import java.util.Arrays;

import org.json.JSONObject;
import org.json.JSONException;

public class FontsPicker extends SettingsPreferenceFragment {

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private String mCategory = FONT_KEY;

    private List<String> mPkgs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.theme_customization_font_title);

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
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 1);
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fonts_option, parent, false);
            CustomViewHolder vh = new CustomViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(CustomViewHolder holder, final int position) {
            String pkg = mPkgs.get(position);
            String label = getLabel(holder.itemView.getContext(), pkg);

            String currentPackageName = mThemeUtils.getOverlayInfos(mCategory).stream()
                .filter(info -> info.isEnabled())
                .map(info -> info.packageName)
                .findFirst()
                .orElse("android");

            holder.title.setText("android".equals(pkg) ? "Default" : label);
            holder.title.setTextSize(20);
            holder.title.setTypeface(getTypeface(holder.title.getContext(), pkg));
            holder.name.setVisibility(View.GONE);

            if (currentPackageName.equals(pkg)) {
                mAppliedPkg = pkg;
                if (mSelectedPkg == null) {
                    mSelectedPkg = pkg;
                }
            }

            holder.itemView.setActivated(pkg == mSelectedPkg);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateActivatedStatus(mSelectedPkg, false);
                    updateActivatedStatus(pkg, true);
                    mSelectedPkg = pkg;
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
            TextView title;
            public CustomViewHolder(View itemView) {
                super(itemView);
                title = (TextView) itemView.findViewById(R.id.option_title);
                name = (TextView) itemView.findViewById(R.id.option_label);
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

    public Typeface getTypeface(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pkg.equals("android") ? Resources.getSystem()
                    : pm.getResourcesForApplication(pkg);
            return Typeface.create(res.getString(
                    res.getIdentifier("config_bodyFontFamily",
                    "string", pkg)), Typeface.NORMAL);
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
    }
}
