/*
 * Copyright (C) 2016-2018 crDroid Android Project
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
package com.crdroid.settings.fragments.about.update.configs;

import android.content.Context;

import com.crdroid.settings.fragments.about.update.xml.OTALink;
import com.crdroid.settings.fragments.about.update.xml.OTAParser;
import com.crdroid.settings.fragments.about.update.utils.OTAUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LinkConfig {

    private static final String FILENAME = "update_links_config";

    private static LinkConfig mInstance;
    private List<OTALink> mLinks;

    private LinkConfig() {
    }

    public static LinkConfig getInstance() {
        if (mInstance == null) {
            mInstance = new LinkConfig();
        }
        return mInstance;
    }

    public static void persistLinks(List<OTALink> links, Context context) {
        try {
            File dir = context.getFilesDir();
            File file = new File(dir, FILENAME);
            if (file.exists()) {
                file.delete();
            }

            FileOutputStream fos = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);

            JSONArray jsonLinks = new JSONArray();
            for (OTALink link : links) {
                JSONObject jsonLink = new JSONObject();
                jsonLink.put(OTAParser.ID, link.getId());
                jsonLink.put(OTAParser.TITLE, link.getTitle());
                jsonLink.put(OTAParser.DESCRIPTION, link.getDescription());
                jsonLink.put(OTAParser.URL, link.getUrl());
                jsonLinks.put(jsonLink);
            }

            fos.write(jsonLinks.toString().getBytes());
            fos.close();

            LinkConfigListener listener = getLinkConfigListener(context);
            if (listener != null) {
                listener.onConfigChange();
            }
        } catch (IOException | JSONException e) {
            OTAUtils.logError(e);
        }
    }

    public List<OTALink> getLinks(Context context, boolean force) {
        if (mLinks == null || force) {
            try {
                mLinks = new ArrayList<>();

                FileInputStream fis = context.openFileInput(FILENAME);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                StringBuffer out = new StringBuffer();
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }
                reader.close();
                fis.close();

                JSONArray jsonLinks = new JSONArray(out.toString());
                for (int i = 0; i < jsonLinks.length(); i++) {
                    JSONObject jsonLink = jsonLinks.getJSONObject(i);
                    OTALink link = new OTALink(jsonLink.getString(OTAParser.ID));
                    link.setTitle(jsonLink.getString(OTAParser.TITLE));
                    link.setDescription(jsonLink.getString(OTAParser.DESCRIPTION));
                    link.setUrl(jsonLink.getString(OTAParser.URL));
                    mLinks.add(link);
                }
            } catch (JSONException | IOException e) {
                OTAUtils.logError(e);
            }
        }
        return mLinks;
    }

    public OTALink findLink(String linkId, Context context) {
        List<OTALink> links = getLinks(context, false);
        for (OTALink link : links) {
            if (link.getId().equalsIgnoreCase(linkId)) {
                return link;
            }
        }
        return null;
    }

    public interface LinkConfigListener {
        void onConfigChange();
    }

    private static LinkConfigListener getLinkConfigListener(Context context) {
        if (context instanceof LinkConfigListener) {
            return (LinkConfigListener) context;
        }
        return null;
    }
}
