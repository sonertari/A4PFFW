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

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;

public class StatsDaily extends StatsGeneral {

    private TextView tvAllMonths;
    private TextView tvAllDays;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.stats_daily, container, false);

        view.findViewById(R.id.logFile).setOnClickListener(mLabelClickedHandler);

        tvMonthDay = (TextView) view.findViewById(R.id.logMonthDay);
        tvMonthDay.setOnClickListener(mLabelClickedHandler);

        tvDaily = (TextView) view.findViewById(R.id.hourly);
        tvDaily.setOnClickListener(mLabelClickedHandler);

        tvAllMonths = (TextView) view.findViewById(R.id.allMonths);
        tvAllMonths.setOnClickListener(mLabelClickedHandler);

        tvAllDays = (TextView) view.findViewById(R.id.allDays);
        tvAllDays.setOnClickListener(mLabelClickedHandler);

        view.findViewById(R.id.defaults).setOnClickListener(mLabelClickedHandler);

        init();
        createStatsViews();

        if (cache.statsDaily == null) {
            cache.statsDaily = new StatsCache();
        }
        mModuleCache = cache.statsDaily;

        configureStatsViews();

        updateDateTimeText();

        return view;
    }

    @Override
    protected void restoreState() {
        fragment = this;
        mJsonStats = mModuleCache.mJsonStats;

        if (mJsonStats == null) {
            getStats();
        } else {
            restoreBaseState();

            if (mModuleCache.bundle.containsKey("tvDaily")) {
                tvDaily.setText(mModuleCache.bundle.getString("tvDaily"));
            }
            if (mModuleCache.bundle.containsKey("tvAllMonths")) {
                tvAllMonths.setText(mModuleCache.bundle.getString("tvAllMonths"));
            }
            if (mModuleCache.bundle.containsKey("tvAllDays")) {
                tvAllDays.setText(mModuleCache.bundle.getString("tvAllDays"));
            }

            updateDateTimeText();
            updateLogFileText();
            updateStats();
        }
    }

    @Override
    protected void saveState() {
        saveBaseState();
        mModuleCache.bundle.putString("tvDaily", tvDaily.getText().toString());
        mModuleCache.bundle.putString("tvAllMonths", tvAllMonths.getText().toString());
        mModuleCache.bundle.putString("tvAllDays", tvAllDays.getText().toString());
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
                }

                mLastLogFile = mLogFile;
            }

            String collect = isDailyChart() ? "''" : "'COLLECT'";
            String month = isAllMonths() ? "" : mMonth;
            String day = isAllDays() ? "" : mDay;

            JSONObject date = new JSONObject().put("Month", month).put("Day", day);
            output = controller.execute("pf", "GetStats", mLogFile, date, collect);

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
            if (!isDailyChart()) {
                // Clear all
                mJsonHourStats = new JSONObject();

                if (mJsonStats != null) {
                    // TODO: Is there a better way?
                    if (mJsonStats.optJSONObject(formatDate()) != null &&
                            mJsonStats.getJSONObject(formatDate()).optJSONObject("Hours") != null) {
                        mJsonHourStats = mJsonStats.getJSONObject(formatDate()).getJSONObject("Hours");
                    }
                }
            }

            recreateStatsViews();

            String[] keys = mStats.keySet().toArray(new String[0]);
            for (String k : keys) {
                updateChartData(k);
                updateListsData(k);

                updateChart(k);
                updateLists(k);
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateStats exception: " + e.toString());
        }
    }

    @Override
    void updateDateTimeText() {
        String month = isAllMonths() ? "-" : mMonth;
        String day = isAllDays() ? "-" : mDay;
        tvMonthDay.setText(String.format(getString(R.string.month_day_colon), month, day));
    }

    void setChartType(String type) {
        if (!isAllMonths() && !isAllDays()) {
            tvDaily.setText(getString(R.string.hourly));
        } else {
            tvDaily.setText(type);
        }
    }

    private boolean isAllMonths() {
        return tvAllMonths.getText().toString().equals(getString(R.string.all_months));
    }

    private void setAllMonths(String type) {
        tvAllMonths.setText(type);

        if (!isAllMonths() && !isAllDays()) {
            setChartType(getString(R.string.hourly));
        }

        if (isAllMonths()) {
            setAllDays(getString(R.string.all_days));
        }
    }

    private boolean isAllDays() {
        return tvAllDays.getText().toString().equals(getString(R.string.all_days));
    }

    private void setAllDays(String type) {
        tvAllDays.setText(type);

        if (!isAllMonths() && !isAllDays()) {
            setChartType(getString(R.string.hourly));
        }

        if (!isAllDays()) {
            setAllMonths(getString(R.string.single_month));
        }
    }

    private void setDefaults() {
        setAllMonths(getString(R.string.all_months));
        setAllDays(getString(R.string.all_days));
        setChartType(getString(R.string.daily));
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                switch (id) {
                    case R.id.hourly:
                        setChartType(isDailyChart() ? getString(R.string.hourly) : getString(R.string.daily));
                        break;
                    case R.id.allMonths:
                        setAllMonths(isAllMonths() ? getString(R.string.single_month) : getString(R.string.all_months));
                        break;
                    case R.id.allDays:
                        setAllDays(isAllDays() ? getString(R.string.single_day) : getString(R.string.all_days));
                        break;
                    case R.id.defaults:
                        setDefaults();
                        break;
                    default:
                        android.app.FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();

                        if (id == R.id.logFile) {
                            ((MainActivity) getActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
                            ((MainActivity) getActivity()).logFilePickerDialog.show(ft, "Selection Dialog");
                        } else {
                            DialogFragment dialog = new DialogFragment();
                            Bundle args = new Bundle();
                            if (id == R.id.logMonthDay) {
                                dialog = new StatsDatePickerDialog();
                                try {
                                    args.putInt("month", Integer.parseInt(mMonth));
                                } catch (Exception e) {
                                    args.putInt("month", Calendar.getInstance().get(Calendar.MONTH));
                                }
                                try {
                                    args.putInt("day", Integer.parseInt(mDay));
                                } catch (Exception e) {
                                    args.putInt("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
                                }
                            }

                            dialog.setArguments(args);
                            dialog.show(ft, "Selection Dialog");
                        }
                        break;
                }

                updateDateTimeText();

            } catch (Exception e) {
                e.printStackTrace();
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
            }
        }
    };
}
