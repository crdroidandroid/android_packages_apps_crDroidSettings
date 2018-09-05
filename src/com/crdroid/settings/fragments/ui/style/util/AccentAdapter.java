/*
 * Copyright (C) 2018 crDroid Android Project
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
package com.crdroid.settings.fragments.ui.style.util;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.ui.style.models.Accent;

import java.util.List;

public class AccentAdapter extends BaseAdapter {
    private List<Accent> mAccents;
    private Context mContext;
    private boolean isDark;

    public AccentAdapter(List<Accent> accents, Context context) {
        mAccents = accents;
        mContext = context;
    }

    @Override
    public int getCount() {
        return mAccents.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, @Nullable View argView, @NonNull ViewGroup parent) {
        View view = generateItemView(argView);
        if (view == null) {
            return null;
        }

        Resources r = mContext.getResources();
        Accent accent = mAccents.get(position);

        ImageView preview = view.findViewById(R.id.item_accent_preview);
        TextView name = view.findViewById(R.id.item_accent_name);

        preview.setImageDrawable(UIUtils.getAccentBitmap(r,
                r.getDimensionPixelSize(R.dimen.style_accent_preview), accent.getColor()));
        name.setText(accent.getName());

        return view;
    }

    @Nullable
    private View generateItemView(@Nullable View argView) {
        if (argView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            return (inflater == null) ? null :
                    inflater.inflate(R.layout.style_item_accent_preview, null);
        } else {
            return argView;
        }
    }
}
