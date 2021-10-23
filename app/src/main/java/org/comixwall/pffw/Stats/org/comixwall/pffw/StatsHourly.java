/*
 * Copyright (C) 2017-2021 Soner Tari
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

import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TimePicker;

import com.github.mikephil.charting.data.BarEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;

public class StatsHourly extends StatsBase implements TimePickerDialog.OnTimeSetListener {

    private JSONObject mJsonAllMinuteStats = new JSONObject();
    private JSONArray mJsonMinutes = new JSONArray();

    String mHour = "00";

    private TextView tvHour;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.stats_hourly, container, false);

        view.findViewById(R.id.logFile).setOnClickListener(mLabelClickedHandler);

        tvMonthDay = view.findViewById(R.id.logMonthDay);
        tvMonthDay.setOnClickListener(mLabelClickedHandler);

        tvHour = view.findViewById(R.id.logHour);
        tvHour.setOnClickListener(mLabelClickedHandler);

        init();
        createStatsViews();

        if (cache.statsHourly == null) {
            cache.statsHourly = new StatsCache();
        }
        mModuleCache = cache.statsHourly;

        configureStatsViews();

        updateDateTimeText();

        return view;
    }

    protected void restoreState() {
        fragment = this;
        mJsonStats = mModuleCache.mJsonStats;

        if (mJsonStats == null) {
            getStats();
        } else {
            restoreBaseState();
            mHour = mModuleCache.bundle.getString("mHour");

            updateDateTimeText();
            updateLogFileText();
            updateStats();
        }
    }

    protected void saveState() {
        saveBaseState();
        mModuleCache.bundle.putString("mHour", mHour);
    }

    protected boolean isDailyChart() {
        return false;
    }

    protected void configureHorizontalBarChart(String k) {
    }

    @Override
    protected boolean fetchStats() {
        try {
            String output = controller.execute("pf", "SelectLogFile", mLogFile);

            mLogFile = new JSONArray(output).get(0).toString();

            if (isLogFileChanged()) {
                output = controller.execute("pf", "GetLogStartDate", mLogFile);

                // Dec 09 21:03:54
                Pattern p = Pattern.compile("^(\\w+)\\s+(\\d+)\\s+(\\d+):\\d+:\\d+$");
                Matcher m = p.matcher(new JSONArray(output).get(0).toString());
                if (m.matches()) {
                    mMonth = monthNumbers.get(m.group(1));
                    mDay = m.group(2);
                    mHour = m.group(3);
                }

                mLastLogFile = mLogFile;
            }

            JSONObject date = new JSONObject().put("Month", mMonth).put("Day", mDay).put("Hour", mHour);
            output = controller.execute("pf", "GetStats", mLogFile, date, "COLLECT");

            mJsonStats = new JSONObject(new JSONArray(output).get(0).toString()).optJSONObject("Date");

            output = controller.execute("pf", "GetLogFilesList");

            mJsonLogFileList = new JSONObject(new JSONArray(output).get(0).toString());

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    @Override
    protected void updateStats() {
        try {
            // Clear all
            mJsonHourStats = new JSONObject();
            mJsonAllMinuteStats = new JSONObject();
            mJsonMinutes = new JSONArray();

            if (mJsonStats != null) {
                // TODO: Is there a better way?
                if (mJsonStats.optJSONObject(formatDate()) != null &&
                        mJsonStats.getJSONObject(formatDate()).optJSONObject("Hours") != null &&
                        mJsonStats.getJSONObject(formatDate()).getJSONObject("Hours").optJSONObject(mHour) != null) {
                    mJsonHourStats = mJsonStats.getJSONObject(formatDate()).getJSONObject("Hours").getJSONObject(mHour);
                    if (mJsonHourStats.has("Mins")) {
                        mJsonAllMinuteStats = mJsonHourStats.getJSONObject("Mins");
                        mJsonMinutes = mJsonAllMinuteStats.names();
                    }
                }
            }

            String[] keys = mStats.keySet().toArray(new String[0]);
            for (String k : keys) {
                updateChartData(k);
                updateStatsLists(k);

                updateChart(k);
                updateLists(k);
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateStats exception: " + e.toString());
        }
    }

    private void updateChartData(String k) {
        try {
            ArrayList<BarEntry> values = Objects.requireNonNull(mStats.get(k)).values;
            values.clear();

            int j = 0;

            String m = "-1";
            if (!mJsonMinutes.isNull(j)) {
                m = mJsonMinutes.get(j).toString();
            }

            for (int i = 0; i < 60; i++) {
                String count = "0";

                if (Integer.parseInt(m) == i) {
                    if (mJsonAllMinuteStats.has(m)) {
                        JSONObject minuteStats = mJsonAllMinuteStats.getJSONObject(m);

                        if (minuteStats.has(k)) {
                            count = minuteStats.getString(k);
                        }

                        j++;
                        if (!mJsonMinutes.isNull(j)) {
                            m = mJsonMinutes.getString(j);
                        }
                    }
                }

                values.add(i, new BarEntry(i, Integer.parseInt(count)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateChartData exception: " + e.toString());
        }
    }

    // Top lists
    private void updateStatsLists(String k) {
        try {
            // Clear the stats first, in case there is no data
            Objects.requireNonNull(mStats.get(k)).total = "0";

            StatsKey2List k2ls = Objects.requireNonNull(mStats.get(k)).lists;
            String[] chartKeys = k2ls.keySet().toArray(new String[0]);

            for (String key : chartKeys) {
                Objects.requireNonNull(k2ls.get(key)).clear();
            }

            if (mJsonHourStats.has(k)) {
                JSONObject cks = mJsonHourStats.getJSONObject(k);

                Objects.requireNonNull(mStats.get(k)).total = cks.getString("Sum");

                for (String key : chartKeys) {
                    StatsList list = k2ls.get(key);

                    JSONObject vs = cks.getJSONObject(key);

                    JSONArray statsKeys = vs.names();
                    for (int i = 0; i < Objects.requireNonNull(statsKeys).length(); i++) {
                        String sk = statsKeys.getString(i);
                        String v = vs.getString(sk);
                        Objects.requireNonNull(list).put(sk, Integer.parseInt(v));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateStatsLists exception: " + e.toString());
        }
    }

    @Override
    void updateDateTimeText() {
        tvMonthDay.setText(String.format(getString(R.string.month_day_colon), mMonth, mDay));
        tvHour.setText(String.format(getString(R.string.hour_colon), mHour));
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                android.app.FragmentTransaction ft = requireActivity().getFragmentManager().beginTransaction();

                if (id == R.id.logFile) {
                    ((MainActivity) requireActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
                    ((MainActivity) requireActivity()).logFilePickerDialog.show(ft, "Selection Dialog");
                } else {
                    DialogFragment dialog = new DialogFragment();
                    Bundle args = new Bundle();
                    if (id == R.id.logMonthDay) {
                        dialog = new StatsDatePickerDialog();
                        args.putInt("month", Integer.parseInt(mMonth));
                        args.putInt("day", Integer.parseInt(mDay));
                    } else if (id == R.id.logHour) {
                        dialog = new StatsTimePickerDialog();
                        args.putInt("hour", Integer.parseInt(mHour));
                    }

                    dialog.setArguments(args);
                    dialog.show(ft, "Selection Dialog");

                    updateDateTimeText();
                }

            } catch (Exception e) {
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mHour = String.format("%02d", hourOfDay);
        updateDateTimeText();
    }
}
