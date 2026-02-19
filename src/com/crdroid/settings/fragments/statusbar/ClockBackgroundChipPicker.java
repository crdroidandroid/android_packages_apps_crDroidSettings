/*
 * Copyright (C) 2026 crDroid Android Project
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

package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class ClockBackgroundChipPicker extends SettingsPreferenceFragment {

    private RecyclerView mRecyclerView;

    private String[] mIcons;

    private static final String SETTING_KEY = "statusbar_clock_chip";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private Resources mSysUiRes;

    private String[] mChipDrawables;
    private String[] mChipLabels;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.statusbar_clock_chip_title);

        mChipDrawables = getResources().getStringArray(R.array.statusbar_clock_chip_drawables);
        mChipLabels = getResources().getStringArray(R.array.statusbar_clock_chip_labels);

        if (mChipLabels.length != mChipDrawables.length) {
            mChipLabels = mChipDrawables;
        }

        try {
            mSysUiRes = getActivity().getPackageManager().getResourcesForApplication(SYSTEMUI_PACKAGE);
        } catch (PackageManager.NameNotFoundException e) {
            mSysUiRes = null;
        }
    }

    @Nullable
    private Drawable getIconDrawable(int styleIndex) {
        if (mSysUiRes == null) return null;
        if (styleIndex <= 0 || styleIndex >= mChipDrawables.length) return null;

        String name = mChipDrawables[styleIndex];
        int id = mSysUiRes.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE);
        if (id == 0) return null;

        try {
            return mSysUiRes.getDrawable(id, getContext().getTheme());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.item_view, container, false);

        int span = (getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE) ? 2 : 1;
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), span));
        IconAdapter mIconAdapter = new IconAdapter(getActivity());
        mRecyclerView.setAdapter(mIconAdapter);

        return view;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int span = (newConfig.orientation
            == Configuration.ORIENTATION_LANDSCAPE) ? 2 : 1;

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            ((GridLayoutManager) layoutManager).setSpanCount(span);
        }
    }

    public static void reset(Context context) {
        Settings.System.putIntForUser(context.getContentResolver(),
            SETTING_KEY, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    public class IconAdapter extends RecyclerView.Adapter<IconAdapter.IconViewHolder> {
        Context context;
        String mSelectedIcon;
        String mAppliedIcon;

        public IconAdapter(Context context) {
            this.context = context;
        }

        @Override
        public IconViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.clock_chip_option, parent, false);
            IconViewHolder vh = new IconViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(IconViewHolder holder, final int position) {
            int styleIndex = position;

            Drawable preview = getIconDrawable(styleIndex);
            holder.clock.setBackground(preview);
            holder.clock.setPadding(8, 2, 8, 2);
            holder.clock.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            if (styleIndex != 0 && styleIndex != 2 && styleIndex != 8) {
                holder.clock.setTextColor(Color.WHITE);
            }
            holder.name.setVisibility(View.VISIBLE);
            holder.name.setText(mChipLabels[position]);

            int applied = Settings.System.getIntForUser(
                    context.getContentResolver(),
                    SETTING_KEY,
                    0,
                    UserHandle.USER_CURRENT);

            if (position == applied) {
                mAppliedIcon = String.valueOf(styleIndex);
                if (mSelectedIcon == null) mSelectedIcon = mAppliedIcon;
            }

            holder.itemView.setActivated(String.valueOf(styleIndex).equals(mSelectedIcon));

            holder.itemView.setOnClickListener(v -> {
                updateActivatedStatus(mSelectedIcon, false);
                updateActivatedStatus(String.valueOf(styleIndex), true);
                mSelectedIcon = String.valueOf(styleIndex);

                Settings.System.putIntForUser(
                        getActivity().getContentResolver(),
                        SETTING_KEY,
                        styleIndex,
                        UserHandle.USER_CURRENT);
            });
        }

        @Override
        public int getItemCount() {
            return mChipDrawables.length;
        }

        private void updateActivatedStatus(String styleIndexStr, boolean isActivated) {
            if (styleIndexStr == null) return;
            int index;
            try {
                index = Integer.parseInt(styleIndexStr);
            } catch (NumberFormatException e) {
                return;
            }
            if (index < 0 || index >= mChipDrawables.length) return;

            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.itemView != null) {
                holder.itemView.setActivated(isActivated);
            }
        }

        public class IconViewHolder extends RecyclerView.ViewHolder {
            TextView clock;
            TextView name;
            public IconViewHolder(View itemView) {
                super(itemView);
                clock = (TextView) itemView.findViewById(R.id.option_clock);
                name = (TextView) itemView.findViewById(R.id.option_label);
            }
        }
    }
}
