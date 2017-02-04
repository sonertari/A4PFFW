/*
 * Copyright (C) 2017 Soner Tari
 *
 * This file is part of PFFW.
 *
 * PFFW is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PFFW is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PFFW.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.comixwall.pffw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.Utils.processException;

public class StatsLive extends StatsHourly implements RefreshTimer.OnTimeoutListener {

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private String mMinute = "00";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.stats_live, container, false);

        tvMonthDay = (TextView) view.findViewById(R.id.systemDatetime);

        init();
        createStatsViews(savedInstanceState);

        if (cache.statsLive == null) {
            cache.statsLive = new StatsCache();
        }
        mModuleCache = cache.statsLive;

        updateDateTimeText();
        configureStatsViews();

        return view;
    }

    protected void saveState() {
        saveBaseState();
        mModuleCache.bundle.putString("mHour", mHour);
        mModuleCache.bundle.putString("mMinute", mMinute);

        /// @attention It is very important to cancel the timer
        mTimer.cancel();
    }

    protected void restoreState() {
        fragment = this;
        mJsonStats = mModuleCache.mJsonStats;

        if (mJsonStats == null) {
            getStats();
        } else {
            restoreBaseState();
            mHour = mModuleCache.bundle.getString("mHour");
            mMinute = mModuleCache.bundle.getString("mMinute");

            updateDateTimeText();
            updateStats();
        }

        /// @todo Android SDK recommends the Handler class instead
        // see http://stackoverflow.com/questions/4597690/android-timer-how
        // But the Timer class is very simple to use.
        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        /// @attention Do not use scheduleAtFixedRate(), we don't need fixed-rate.
        // If a refresh task is delayed, the rest of the ticks should delay too.
        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getStats()
        mTimer.start(mRefreshTimeout);
    }

    @Override
    public void onTimeout() {
        getStats();
    }

    @Override
    protected boolean fetchStats() {
        try {
            String output = controller.execute("GetDefaultLogFile");

            mLogFile = new JSONArray(output).get(0).toString();

            output = controller.execute("GetDateTime");

            JSONObject jsonDateTime = new JSONObject(new JSONArray(output).get(0).toString());
            mMonth = String.format("%02d", Integer.parseInt(jsonDateTime.getString("Month")));
            mDay = String.format("%02d", Integer.parseInt(jsonDateTime.getString("Day")));
            mHour = String.format("%02d", Integer.parseInt(jsonDateTime.getString("Hour")));
            mMinute = String.format("%02d", Integer.parseInt(jsonDateTime.getString("Minute")));

            JSONObject date = new JSONObject().put("Month", mMonth).put("Day", mDay).put("Hour", mHour);
            output = controller.execute("GetStats", mLogFile, date, "COLLECT");

            mJsonStats = new JSONObject(new JSONArray(output).get(0).toString()).optJSONObject("Date");

            output = controller.execute("GetReloadRate");

            int timeout = Integer.parseInt(new JSONArray(output).get(0).toString());
            mRefreshTimeout = timeout < 10 ? 10 : timeout;

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    protected void updateLogFileText() {
    }

    @Override
    void updateDateTimeText() {
        tvMonthDay.setText(String.format("Date: %s %s, %s:%s", monthNames.get(mMonth), mDay, mHour, mMinute));
    }
}
