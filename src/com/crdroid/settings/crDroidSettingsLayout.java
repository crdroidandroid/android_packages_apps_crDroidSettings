/*
 * Copyright (C) 2017-2018 crDroid Android Project
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

package com.crdroid.settings;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.fab.FloatingActionsMenu;
import com.crdroid.settings.fab.FloatingActionButton;
import com.crdroid.settings.fragments.About;
import com.crdroid.settings.fragments.Buttons;
import com.crdroid.settings.fragments.LockScreen;
import com.crdroid.settings.fragments.Miscellaneous;
import com.crdroid.settings.fragments.Navigation;
import com.crdroid.settings.fragments.Notifications;
import com.crdroid.settings.fragments.QuickSettings;
import com.crdroid.settings.fragments.Recents;
import com.crdroid.settings.fragments.Sound;
import com.crdroid.settings.fragments.StatusBar;
import com.crdroid.settings.fragments.UserInterface;
import com.crdroid.settings.R;

public class crDroidSettingsLayout extends SettingsPreferenceFragment {

    private static final String TAG = "crDroidSettingsLayout";
    ViewPager mViewPager;
    ViewGroup mContainer;
    PagerSlidingTabStrip mTabs;
    SectionsPagerAdapter mSectionsPagerAdapter;
    protected Context mContext;
    private LinearLayout mLayout;
    private FloatingActionsMenu mFab;
    private FrameLayout mInterceptorFrame;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.crdroid_settings_title);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContainer = container;
        View view = inflater.inflate(R.layout.crdroid_settings, container, false);
        mFab = (FloatingActionsMenu) view.findViewById(R.id.fab_menu);
        mInterceptorFrame = (FrameLayout) view.findViewById(R.id.fl_interceptor);
        mLayout = (LinearLayout) view.findViewById(R.id.crdroid_content);
        mViewPager = (ViewPager) view.findViewById(R.id.viewpager);
        mTabs = (PagerSlidingTabStrip) view.findViewById(R.id.tabs);
        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mTabs.setViewPager(mViewPager);
        mContext = getActivity().getApplicationContext();

        mInterceptorFrame.getBackground().setAlpha(0);

        mFab.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
        @Override
        public void onMenuExpanded() {
        mInterceptorFrame.getBackground().setAlpha(240);
        mInterceptorFrame.setOnTouchListener(new View.OnTouchListener() {
             @Override
             public boolean onTouch(View v, MotionEvent event) {
                   mFab.collapse();
                   return true;
                   }
             });
        }

        @Override
        public void onMenuCollapsed() {
                    mInterceptorFrame.getBackground().setAlpha(0);
                    mInterceptorFrame.setOnTouchListener(null);
    	            }
        });

        mInterceptorFrame.setOnTouchListener(new View.OnTouchListener() {
             @Override
             public boolean onTouch(View v, MotionEvent event) {
                if (mFab.isExpanded()) {
                    mFab.collapse();
                    return true;
                }
                return false;
            }
        });

        FloatingActionButton mFab1 = (FloatingActionButton) view.findViewById(R.id.fab_reset);
        mFab1.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 Buttons.reset(mContext);
                 LockScreen.reset(mContext);
                 Miscellaneous.reset(mContext);
                 Navigation.reset(mContext);
                 Notifications.reset(mContext);
                 QuickSettings.reset(mContext);
                 Recents.reset(mContext);
                 Sound.reset(mContext);
                 StatusBar.reset(mContext);
                 UserInterface.reset(mContext);
                 mSectionsPagerAdapter.notifyDataSetChanged();
                 if (mFab.isExpanded())
                     mFab.collapse();
                 finish();
                 startActivity(getIntent());
             }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle saveState) {
        super.onSaveInstanceState(saveState);
    }

    class SectionsPagerAdapter extends FragmentPagerAdapter {

        String titles[] = getTitles();
        private Fragment frags[] = new Fragment[titles.length];

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            frags[0] = new StatusBar();
            frags[1] = new QuickSettings();
            frags[2] = new LockScreen();
            frags[3] = new Recents();
            frags[4] = new Navigation();
            frags[5] = new Buttons();
            frags[6] = new UserInterface();
            frags[7] = new Notifications();
            frags[8] = new Sound();
            frags[9] = new Miscellaneous();
            frags[10] = new About();
        }

        @Override
        public Fragment getItem(int position) {
            return frags[position];
        }

        @Override
        public int getCount() {
            return frags.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    private String[] getTitles() {
        String titleString[];
        titleString = new String[] {
            getString(R.string.statusbar_title),
            getString(R.string.quicksettings_title),
            getString(R.string.lockscreen_title),
            getString(R.string.recents_title),
            getString(R.string.navbar_title),
            getString(R.string.button_title),
            getString(R.string.ui_title),
            getString(R.string.notifications_title),
            getString(R.string.sound_title),
            getString(R.string.misc_title),
            getString(R.string.about_crdroid)
        };
        return titleString;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
     }
}
