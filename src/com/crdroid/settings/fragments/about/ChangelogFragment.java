/*
 * Copyright (C) 2016-2021 crDroid Android Project
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
package com.crdroid.settings.fragments.about;

import android.annotation.Nullable;
import android.app.Fragment;
import android.content.res.Resources;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.preference.PreferenceFragment;

import com.android.settings.R;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.android.internal.logging.nano.MetricsProto;

public class ChangelogFragment extends PreferenceFragment {

    TextView textView;

    private static final String CHANGELOG_PATH = "/system/etc/Changelog.txt";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.changelog, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textView = view.findViewById(R.id.changelog_text);

        InputStreamReader inputReader = null;
        String text = null;
        StringBuilder data = new StringBuilder();

        Pattern date = Pattern.compile("(={20}|\\d{4}-\\d{2}-\\d{2})");
        Pattern commit = Pattern.compile("([a-f0-9]{7})");
        Pattern committer = Pattern.compile("\\[(\\D.*?)]");
        Pattern title = Pattern.compile("(\\R\\s+[\\*]\\s.*)");

        try {
            char tmp[] = new char[2048];
            int numRead;

            inputReader = new FileReader(CHANGELOG_PATH);
            while ((numRead = inputReader.read(tmp)) >= 0) {
                data.append(tmp, 0, numRead);
            }
//            text = data.toString();
        } catch (IOException e) {
//            text = getString(R.string.changelog_error);
        } finally {
            try {
                if (inputReader != null) {
                    inputReader.close();
                }
            } catch (IOException e) {
            }
        }

        SpannableStringBuilder sb = new SpannableStringBuilder(data);
        Resources.Theme theme = getContext().getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        final int color = getContext().getColor(typedValue.resourceId);

        Matcher m = date.matcher(data);
        while (m.find()){
            sb.setSpan(new ForegroundColorSpan(color), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        m = commit.matcher(data);
        while (m.find()){
            sb.setSpan(new StyleSpan(Typeface.NORMAL), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        m = committer.matcher(data);
        while (m.find()){
            sb.setSpan(new ForegroundColorSpan(color), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.NORMAL), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        m = title.matcher(data);
        while (m.find()){
            sb.setSpan(new ForegroundColorSpan(color), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        textView.setText(sb);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }
}
