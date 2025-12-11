/*
 * Copyright (C) 2022-2025 crDroid Android Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.util.crdroid.ThemeUtils;

import java.lang.ref.WeakReference;
import java.util.List;

public class WifiIcons extends SettingsPreferenceFragment {

    private static final String TAG = "WifiIcons";

    private RecyclerView mRecyclerView;
    private ThemeUtils mThemeUtils;
    private final String mCategory = "android.theme.customization.wifi_icon";
    private List<String> mPkgs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isAdded()) return;
        requireActivity().setTitle(R.string.theme_customization_wifi_icon_title);
        mThemeUtils = new ThemeUtils(requireContext());
        mPkgs = mThemeUtils.getOverlayPackagesForCategory(mCategory, "android");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_view, container, false);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mRecyclerView.setAdapter(new Adapter(requireContext(), mPkgs, mThemeUtils, mCategory, mRecyclerView));
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    public static class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        private final WeakReference<Context> contextRef;
        private final List<String> mPkgs;
        private final ThemeUtils mThemeUtils;
        private final String mCategory;
        private final RecyclerView mRecyclerView;
        private final String mAppliedPkg;
        private String mSelectedPkg;

        public Adapter(Context context, List<String> pkgs, ThemeUtils themeUtils, String category, RecyclerView recyclerView) {
            this.contextRef = new WeakReference<>(context);
            this.mPkgs = pkgs;
            this.mThemeUtils = themeUtils;
            this.mCategory = category;
            this.mRecyclerView = recyclerView;

            mAppliedPkg = mThemeUtils.getOverlayInfos(mCategory).stream()
                    .filter(info -> info.isEnabled())
                    .map(info -> info.packageName)
                    .findFirst()
                    .orElse("android");
            mSelectedPkg = mAppliedPkg;
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.icon_option, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder holder, int position) {
            Context context = contextRef.get();
            if (context == null) return;

            String pkg = mPkgs.get(position);

            holder.image1.setBackgroundDrawable(getDrawable(context, pkg, "ic_wifi_signal_0"));
            holder.image2.setBackgroundDrawable(getDrawable(context, pkg, "ic_wifi_signal_2"));
            holder.image3.setBackgroundDrawable(getDrawable(context, pkg, "ic_wifi_signal_3"));
            holder.image4.setBackgroundDrawable(getDrawable(context, pkg, "ic_wifi_signal_4"));

            String label = getLabel(context, pkg);
            holder.name.setText("android".equals(pkg) ? "Default" : label);
            holder.itemView.setActivated(pkg.equals(mSelectedPkg));

            holder.itemView.setOnClickListener(view -> {
                if (!pkg.equals(mSelectedPkg)) {
                    String oldPkg = mSelectedPkg;
                    mSelectedPkg = pkg;
                    mThemeUtils.setOverlayEnabled(mCategory, oldPkg, oldPkg);
                    mThemeUtils.setOverlayEnabled(mCategory, pkg, "android");
                }
                updateActivatedStatus();
            });
        }

        @Override
        public int getItemCount() {
            return mPkgs.size();
        }

        private void updateActivatedStatus() {
            notifyDataSetChanged();
        }

        public static class CustomViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image1, image2, image3, image4;

            public CustomViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.option_label);
                image1 = itemView.findViewById(R.id.image1);
                image2 = itemView.findViewById(R.id.image2);
                image3 = itemView.findViewById(R.id.image3);
                image4 = itemView.findViewById(R.id.image4);
            }
        }

        private Drawable getDrawable(Context context, String pkg, String drawableName) {
            try {
                PackageManager pm = context.getPackageManager();
                Resources res = pkg.equals("android") ? Resources.getSystem() : pm.getResourcesForApplication(pkg);
                int resId = res.getIdentifier(drawableName, "drawable", pkg);
                if (resId != 0) {
                    return res.getDrawable(resId, context.getTheme());
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Drawable load failed for pkg: " + pkg + ", name: " + drawableName, e);
            }
            return null;
        }

        private String getLabel(Context context, String pkg) {
            PackageManager pm = context.getPackageManager();
            try {
                return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Label load failed for pkg: " + pkg, e);
            }
            return pkg;
        }
    }
}
