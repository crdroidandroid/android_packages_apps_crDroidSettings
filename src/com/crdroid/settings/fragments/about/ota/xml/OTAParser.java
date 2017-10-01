/*
 * Copyright (C) 2016-2017 crDroid Android Project
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
package com.crdroid.settings.fragments.about.ota.xml;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class OTAParser {

    private static final String ns = null;
    private static final String FILENAME_TAG = "Filename";
    private static final String URL_TAG = "Url";

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String URL = "url";

    private String mDeviceName = null;
    private String mReleaseType = null;
    private OTADevice mDevice = null;

    private static OTAParser mInstance;

    private OTAParser() {
    }

    public static OTAParser getInstance() {
        if (mInstance == null) {
            mInstance = new OTAParser();
        }
        return mInstance;
    }

    public OTADevice parse(InputStream in, String deviceName, String releaseType) throws XmlPullParserException, IOException {
        this.mDeviceName = deviceName;
        this.mReleaseType = releaseType;

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            readBuildType(parser);
            return mDevice;
        } finally {
            in.close();
        }
    }

    private void readBuildType(XmlPullParser parser) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase(mReleaseType)) {
                readStable(parser);
            } else {
                skip(parser);
            }
        }
    }

    private void readStable(XmlPullParser parser) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase(mDeviceName)) {
                readDevice(parser);
            } else {
                skip(parser);
            }
        }
    }

    private void readDevice(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, mDeviceName);
        mDevice = new OTADevice();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equalsIgnoreCase(FILENAME_TAG)) {
                String tagValue = readTag(parser, tagName);
                mDevice.setLatestVersion(tagValue);
            } else if (isUrlTag(tagName)) {
                OTALink link = readLink(parser, tagName);
                mDevice.addLink(link);
            } else {
                skip(parser);
            }
        }
    }

    private OTALink readLink(XmlPullParser parser, String tag) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, tag);

        String id = parser.getAttributeValue(null, ID);
        if (id == null || id.isEmpty()) {
            id = tag;
        }
        OTALink link = new OTALink(id);
        String title = parser.getAttributeValue(null, TITLE);
        link.setTitle(title);
        String description = parser.getAttributeValue(null, DESCRIPTION);
        link.setDescription(description);
        String url = readText(parser);
        link.setUrl(url);

        parser.require(XmlPullParser.END_TAG, ns, tag);
        return link;
    }

    private String readTag(XmlPullParser parser, String tag) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, tag);
        String text = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, tag);
        return text;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private static boolean isUrlTag(String tagName) {
        return tagName.toLowerCase().endsWith(URL_TAG.toLowerCase());
    }
}
