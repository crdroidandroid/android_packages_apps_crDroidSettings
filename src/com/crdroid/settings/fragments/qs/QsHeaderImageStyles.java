/*
 * Copyright (C) 2023 crDroid Android Project
 * Copyright (C) 2023 AlphaDroid
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

package com.crdroid.settings.fragments.qs;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
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
import android.text.TextUtils;
import androidx.preference.PreferenceViewHolder;
import android.view.ViewGroup.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.crdroid.ImageHelper;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.Indexable;
import com.android.settingslib.Utils;
import com.android.settings.SettingsPreferenceFragment;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.json.JSONObject;
import org.json.JSONException;

public class QsHeaderImageStyles extends SettingsPreferenceFragment {

    private static final int HEADER_COUNT = 24;

    private RecyclerView mRecyclerView;
    private List<String> mQsHeaderImages;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.qs_header_image_title);
        mQsHeaderImages = loadHeadersList();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.item_view, container, false);
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
        String mSelectedImage;
        String mAppliedImage;

        public Adapter(Context context) {
            this.context = context;
        }

        @Override
        public CustomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.qs_header_image_option, parent, false);
            CustomViewHolder vh = new CustomViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(CustomViewHolder holder, final int position) {

            int currentHeaderNumber = getCurrentHeaderNumber();

            String loadedImage = mQsHeaderImages.get(position);
            Bitmap background = getBitmap(holder.qsHeaderImage.getContext(), loadedImage);
            float radius = getContext().getResources().getDimensionPixelSize(Utils.getThemeAttr(
                                    getContext(), android.R.attr.dialogCornerRadius));
            holder.qsHeaderImage.setImageBitmap(ImageHelper.getRoundedCornerBitmap(background, radius));

            if (currentHeaderNumber == (position + 1)) {
                mAppliedImage = loadedImage;
                if (mSelectedImage == null) {
                    mSelectedImage = loadedImage;
                }
            }

            holder.itemView.setActivated(loadedImage == mSelectedImage);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ContentResolver resolver = getContext().getContentResolver();
                    updateActivatedStatus(mSelectedImage, false);
                    updateActivatedStatus(loadedImage, true);
                    mSelectedImage = loadedImage;
                    Settings.System.putIntForUser(resolver,
                            Settings.System.QS_HEADER_IMAGE, position + 1,
                            UserHandle.USER_CURRENT);
                    if (currentHeaderNumber == -1) {
                        // if previous header was provided by user, clear Uri
                        Settings.System.putStringForUser(resolver,
                                Settings.System.QS_HEADER_IMAGE_URI, "",
                                UserHandle.USER_CURRENT);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return HEADER_COUNT;
        }

        public class CustomViewHolder extends RecyclerView.ViewHolder {
            ImageView qsHeaderImage;

            public CustomViewHolder(View itemView) {
                super(itemView);
                qsHeaderImage = (ImageView) itemView.findViewById(R.id.qs_header_image);
            }
        }

        private void updateActivatedStatus(String image, boolean isActivated) {
            int index = mQsHeaderImages.indexOf(image);
            if (index < 0) {
                return;
            }
            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && holder.itemView != null) {
                holder.itemView.setActivated(isActivated);
            }
        }
    }

    private Bitmap getBitmap(Context context, String drawableName) {
        return ImageHelper.drawableToBitmap(getDrawable(context, drawableName));
    }

    public Drawable getDrawable(Context context, String drawableName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(drawableName, "drawable", "com.android.settings");
        return res.getDrawable(resId);
    }

    private int getCurrentHeaderNumber() {
        return Settings.System.getIntForUser(getContentResolver(),
                Settings.System.QS_HEADER_IMAGE, 0, UserHandle.USER_CURRENT);
    }

    private List<String> loadHeadersList() {
        List<String> headersList = new ArrayList<String>(HEADER_COUNT);
        for (int i = 1; i <= HEADER_COUNT; i++) {
            headersList.add("qs_header_image_" + i);
        }
        return headersList;
    }
}
