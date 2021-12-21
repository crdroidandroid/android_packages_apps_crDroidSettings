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

package com.crdroid.settings.fragments.lockscreen;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.net.Uri;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.Indexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class UdfpsIconPicker extends SettingsPreferenceFragment {

    private RecyclerView mRecyclerView;

    private Resources udfpsRes;

    private String mPkg = "com.crdroid.udfps.resources";

    private String[] mIcons;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.udfps_icon_picker_title);

        loadResources();
    }

    private void loadResources() {
        try {
            PackageManager pm = getActivity().getPackageManager();
            udfpsRes = pm.getResourcesForApplication(mPkg);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mIcons = udfpsRes.getStringArray(udfpsRes.getIdentifier("udfps_icons",
                "array", mPkg));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.item_view, container, false);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 3);
        mRecyclerView.setLayoutManager(gridLayoutManager);
        UdfpsIconAdapter mUdfpsIconAdapter = new UdfpsIconAdapter(getActivity());
        mRecyclerView.setAdapter(mUdfpsIconAdapter);

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

    public class UdfpsIconAdapter extends RecyclerView.Adapter<UdfpsIconAdapter.UdfpsIconViewHolder> {
        Context context;
        String mSelectedIcon;
        String mAppliedIcon;

        public UdfpsIconAdapter(Context context) {
            this.context = context;
        }

        @Override
        public UdfpsIconViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option, parent, false);
            UdfpsIconViewHolder vh = new UdfpsIconViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(UdfpsIconViewHolder holder, final int position) {
            String iconRes = mIcons[position];

            Glide.with(holder.image.getContext())
                    .load("")
                    .placeholder(getDrawable(holder.image.getContext(), mIcons[position]))
                    .into(holder.image);

            holder.image.setPadding(20,20,20,20);

            holder.name.setVisibility(View.GONE);

            if (position == Settings.System.getInt(context.getContentResolver(),
                Settings.System.UDFPS_ICON, 0)) {
                mAppliedIcon = iconRes;
                if (mSelectedIcon == null) {
                    mSelectedIcon = iconRes;
                }
            }
            holder.itemView.setActivated(iconRes == mSelectedIcon);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateActivatedStatus(mSelectedIcon, false);
                    updateActivatedStatus(iconRes, true);
                    mSelectedIcon = iconRes;
                    Settings.System.putInt(getActivity().getContentResolver(),
                            Settings.System.UDFPS_ICON, position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mIcons.length;
        }

        public class UdfpsIconViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image;
            public UdfpsIconViewHolder(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.option_label);
                image = (ImageView) itemView.findViewById(R.id.option_thumbnail);
            }
        }

        private void updateActivatedStatus(String icon, boolean isActivated) {
            int index = Arrays.asList(mIcons).indexOf(icon);
            if (index < 0) {
                return;
            }
            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.itemView != null) {
                holder.itemView.setActivated(isActivated);
            }
        }
    }

    public Drawable getDrawable(Context context, String drawableName) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources res = pm.getResourcesForApplication(mPkg);
            Context ctx = context.createPackageContext(
                    mPkg, Context.CONTEXT_IGNORE_SECURITY);
            return ctx.getDrawable(res.getIdentifier(drawableName, "drawable", mPkg));
        }
        catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
