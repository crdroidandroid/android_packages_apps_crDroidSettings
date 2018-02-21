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
package com.crdroid.settings.fragments.about.update.xml;

public class OTALink {

    private String mId;
    private String mTitle;
    private String mDescription;
    private String mUrl;

    public OTALink(String id) {
        this.mId = id;
    }

    public String getId() {
        return mId == null ? "" : mId;
    }

    public String getTitle() {
        return mTitle == null ? "" : mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public String getDescription() {
        return mDescription == null ? "" : mDescription;
    }

    public void setDescription(String description) {
        this.mDescription = description;
    }

    public String getUrl() {
        return mUrl == null ? "" : mUrl;
    }

    public void setUrl(String url) {
        this.mUrl = url;
    }
}
