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
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Switch;
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
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.SettingsMainSwitchBar;
import com.android.settingslib.search.Indexable;
import com.android.settingslib.widget.OnMainSwitchChangeListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class UdfpsAnimation extends SettingsPreferenceFragment implements
        OnMainSwitchChangeListener {

    private Switch mSwitch;

    private RecyclerView mRecyclerView;
    private String mPkg = "com.crdroid.udfps.resources";
    private AnimationDrawable animation;

    private Resources udfpsRes;

    private String[] mAnims;
    private String[] mAnimPreviews;
    private String[] mTitles;

    private boolean mEnabled;
    private UdfpsAnimAdapter mUdfpsAnimAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.udfps_recog_animation_effect_title);

        loadResources();
    }

    private void loadResources() {
        try {
            PackageManager pm = getActivity().getPackageManager();
            udfpsRes = pm.getResourcesForApplication(mPkg);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mAnims = udfpsRes.getStringArray(udfpsRes.getIdentifier("udfps_animation_styles",
                "array", mPkg));
        mAnimPreviews = udfpsRes.getStringArray(udfpsRes.getIdentifier("udfps_animation_previews",
                "array", mPkg));
        mTitles = udfpsRes.getStringArray(udfpsRes.getIdentifier("udfps_animation_titles",
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
        mUdfpsAnimAdapter = new UdfpsAnimAdapter(getActivity());

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final SettingsMainSwitchBar switchBar = activity.getSwitchBar();
        mSwitch = switchBar.getSwitch();
        mEnabled = Settings.System.getInt(getActivity().getContentResolver(),
                       Settings.System.UDFPS_ANIM, 0) == 1;
        mSwitch.setChecked(mEnabled);
        setEnabled(mEnabled);
        switchBar.setTitle(getActivity().getString(R.string.enable));
        switchBar.addOnSwitchChangeListener(this);
        switchBar.show();
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.UDFPS_ANIM, isChecked ? 1 : 0);
        mSwitch.setChecked(isChecked);
        setEnabled(isChecked);
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            mRecyclerView.setAdapter(mUdfpsAnimAdapter);
        } else {
            mRecyclerView.setAdapter(null);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public class UdfpsAnimAdapter extends RecyclerView.Adapter<UdfpsAnimAdapter.UdfpsAnimViewHolder> {
        Context context;
        String mSelectedAnim;
        String mAppliedAnim;

        public UdfpsAnimAdapter(Context context) {
            this.context = context;
        }

        @Override
        public UdfpsAnimViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option, parent, false);
            UdfpsAnimViewHolder vh = new UdfpsAnimViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(UdfpsAnimViewHolder holder, final int position) {
            String animName = mAnims[position];

            Glide.with(holder.image.getContext())
                    .load("")
                    .placeholder(getDrawable(holder.image.getContext(), mAnimPreviews[position]))
                    .into(holder.image);

            holder.name.setText(mTitles[position]);

            if (position == Settings.System.getInt(context.getContentResolver(),
                Settings.System.UDFPS_ANIM_STYLE, 0)) {
                mAppliedAnim = animName;
                if (mSelectedAnim == null) {
                    mSelectedAnim = animName;
                }
            }

            holder.itemView.setActivated(animName == mSelectedAnim);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateActivatedStatus(mSelectedAnim, false);
                    updateActivatedStatus(animName, true);
                    mSelectedAnim = animName;
                    holder.image.setBackgroundDrawable(getDrawable(v.getContext(), mAnims[position]));
                    animation = (AnimationDrawable) holder.image.getBackground();
                    animation.setOneShot(true);
                    animation.start();
                    Settings.System.putInt(getActivity().getContentResolver(),
                            Settings.System.UDFPS_ANIM_STYLE, position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mAnims.length;
        }

        public class UdfpsAnimViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ImageView image;
            public UdfpsAnimViewHolder(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.option_label);
                image = (ImageView) itemView.findViewById(R.id.option_thumbnail);
            }
        }

        private void updateActivatedStatus(String anim, boolean isActivated) {
            int index = Arrays.asList(mAnims).indexOf(anim);
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
            return res.getDrawable(res.getIdentifier(drawableName, "drawable", mPkg));
        }
        catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
