/*
 * Copyright (C) 2017-2018 Soner Tari
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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;

public class StatsGeneral extends StatsBase {

    private HashMap<String, TableLayout> generalStatsTables;

    private JSONObject mJsonAllStats;
    private JSONObject mJsonBriefStats;
    private JSONObject mJsonGeneralStats;

    TextView tvDaily;
    private boolean mIsLastChartDaily = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.stats_general, container, false);

        view.findViewById(R.id.logFile).setOnClickListener(mLabelClickedHandler);

        tvDaily = view.findViewById(R.id.hourly);
        tvDaily.setOnClickListener(mLabelClickedHandler);

        view.findViewById(R.id.statsGeneralStats).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.statsRequestsByDate).setOnClickListener(mLabelClickedHandler);

        generalStatsTables = new HashMap<>();
        generalStatsTables.put("SrcIP", (TableLayout) view.findViewById(R.id.statsGeneralSrcIPTable));
        generalStatsTables.put("DstIP", (TableLayout) view.findViewById(R.id.statsGeneralDstIPTable));
        generalStatsTables.put("DPort", (TableLayout) view.findViewById(R.id.statsGeneralDPortTable));
        generalStatsTables.put("Type", (TableLayout) view.findViewById(R.id.statsGeneralTypeTable));

        view.findViewById(R.id.statsGeneralSrcIPTable).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.statsGeneralDstIPTable).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.statsGeneralDPortTable).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.statsGeneralTypeTable).setOnClickListener(mLabelClickedHandler);

        init();
        createStatsViews();

        if (cache.statsGeneral == null) {
            cache.statsGeneral = new StatsGeneralCache();
        }
        mModuleCache = cache.statsGeneral;

        configureStatsViews();

        return view;
    }

    protected void restoreState() {
        fragment = this;
        mJsonStats = mModuleCache.mJsonStats;

        if (mJsonStats == null) {
            getStats();
        } else {
            restoreBaseState();

            StatsGeneralCache cache = (StatsGeneralCache) mModuleCache;
            mJsonAllStats = cache.mJsonAllStats;
            mJsonBriefStats = cache.mJsonBriefStats;
            mJsonGeneralStats = cache.mJsonGeneralStats;

            if (mModuleCache.bundle.containsKey("tvDaily")) {
                tvDaily.setText(mModuleCache.bundle.getString("tvDaily"));
            }

            updateLogFileText();
            updateStats();
        }
    }

    protected void saveState() {
        saveBaseState();

        StatsGeneralCache cache = (StatsGeneralCache) mModuleCache;
        cache.mJsonAllStats = mJsonAllStats;
        cache.mJsonBriefStats = mJsonBriefStats;
        cache.mJsonGeneralStats = mJsonGeneralStats;

        mModuleCache.bundle.putString("tvDaily", tvDaily.getText().toString());
    }

    protected void configureHorizontalBarChart(String k) {

        CardView cv = mCardViews.get(k);

        @SuppressLint("CutPasteId") View bc = cv.findViewById(R.id.chart);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bc.getLayoutParams();
        ViewGroup owner = (ViewGroup) bc.getParent();
        owner.removeView(bc);

        HorizontalBarChart hbc = new HorizontalBarChart(getContext());
        hbc.setId(R.id.chart);
        hbc.setLayoutParams(params);
        owner.addView(hbc, 0);

        // ATTENTION: This second look up is not redundant, we set the same id to the horizontal chart above.
        @SuppressLint("CutPasteId") BarChart chart = cv.findViewById(R.id.chart);
        chart.setOnChartValueSelectedListener(this);
        chart.setDrawValueAboveBar(true);
        chart.setDrawBarShadow(false);
        chart.getDescription().setEnabled(false);
        // scaling can now only be done on x- and y-axis separately
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setMaxVisibleValueCount(60);
        chart.animateY(1000);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f); // only intervals of 1
        xAxis.setLabelCount(7);
        xAxis.setValueFormatter(xavf);

        YAxis leftAxis = chart.getAxisLeft();
        // BUG: Need to set leftAxis min to 0, otherwise bars leave a gap to the X-axis and values above bars disappear
        leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)
        leftAxis.setEnabled(false);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setLabelCount(4, false);
        rightAxis.setValueFormatter(yavf);
        rightAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        rightAxis.setSpaceTop(15f);
        rightAxis.setDrawAxisLine(true);
        rightAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

        Legend l = chart.getLegend();
        l.setEnabled(false);

        XYMarkerView mv = new XYMarkerView(view.getContext(), xavf);
        mv.setChartView(chart); // For bounds control
        chart.setMarker(mv); // Set the marker to the chart
    }

    @Override
    protected boolean fetchStats() {
        try {
            String output = controller.execute("pf", "SelectLogFile", mLogFile);

            mLogFile = new JSONArray(output).get(0).toString();

            // Whether to collect hourly stats too
            String collect = isDailyChart() ? "''" : "'COLLECT'";
            output = controller.execute("pf", "GetAllStats", mLogFile, collect);

            mJsonAllStats = new JSONObject(new JSONArray(output).get(0).toString());
            mJsonBriefStats = new JSONObject(mJsonAllStats.getString("briefstats"));
            mJsonStats = new JSONObject(mJsonAllStats.getString("stats")).optJSONObject("Date");

            output = controller.execute("pf", "GetProcStatLines", mLogFile);

            mJsonGeneralStats = new JSONObject(new JSONArray(output).get(0).toString());

            output = controller.execute("pf", "GetLogFilesList");

            mJsonLogFileList = new JSONObject(new JSONArray(output).get(0).toString());

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    private void updateGeneralStatsTable() {
        TableLayout table = view.findViewById(R.id.statsGeneralStats);
        table.removeAllViews();

        Iterator<String> it = mJsonGeneralStats.keys();
        while (it.hasNext()) {
            String key = it.next();
            String count = mJsonGeneralStats.optString(key);

            TableRow row = (TableRow) getActivity().getLayoutInflater().inflate(R.layout.stats_table_row, new TableRow(this.view.getContext()), true);

            ((TextView) row.findViewById(R.id.tableValue)).setText(count);
            ((TextView) row.findViewById(R.id.tableKey)).setText(key);
            table.addView(row);
        }
    }

    private void updateRequestsByDateTable() {
        try {
            TableLayout table = view.findViewById(R.id.statsRequestsByDate);
            table.removeAllViews();

            HashMap<String, Integer> list = new HashMap<>();

            JSONObject values = mJsonBriefStats.getJSONObject("Date");
            JSONArray keys = values.names();
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                String v = values.getString(k);

                int c = Integer.parseInt(v);
                list.put(k, c);
            }

            Object[] kvps = list.entrySet().toArray();
            Arrays.sort(kvps, reverseComparator);

            for (Object entry : kvps) {
                TableRow row = (TableRow) getActivity().getLayoutInflater().inflate(R.layout.stats_table_row, new TableRow(this.view.getContext()), true);

                ((TextView) row.findViewById(R.id.tableValue)).setText(((Map.Entry<String, Integer>) entry).getValue().toString());
                ((TextView) row.findViewById(R.id.tableKey)).setText(((Map.Entry<String, Integer>) entry).getKey());
                table.addView(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateRequestsByDateTable exception: " + e.toString());
        }
    }

    private void updateGeneralStats() {
        try {

            for (String sk : generalStatsTables.keySet()) {
                TableLayout statsTable = generalStatsTables.get(sk);
                statsTable.removeAllViews();

                HashMap<String, Integer> list = new HashMap<>();

                JSONObject values = mJsonBriefStats.getJSONObject(sk);
                JSONArray keys = values.names();
                for (int i = 0; i < keys.length(); i++) {
                    String k = keys.getString(i);
                    String v = values.getString(k);

                    int c = Integer.parseInt(v);
                    list.put(k, c);
                }

                Object[] kvps = list.entrySet().toArray();
                Arrays.sort(kvps, reverseComparator);

                int count = 1;
                for (Object entry : kvps) {
                    TableRow row = (TableRow) getActivity().getLayoutInflater().inflate(R.layout.stats_table_row, new TableRow(this.view.getContext()), true);

                    ((TextView) row.findViewById(R.id.tableValue)).setText(((Map.Entry<String, Integer>) entry).getValue().toString());
                    ((TextView) row.findViewById(R.id.tableKey)).setText(((Map.Entry<String, Integer>) entry).getKey());
                    statsTable.addView(row);

                    if (++count > 100) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateGeneralStats exception: " + e.toString());
        }
    }

    @Override
    protected void updateStats() {
        try {
            updateGeneralStatsTable();
            updateRequestsByDateTable();
            updateGeneralStats();

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

            // Recreate the stats views in case the chart type has changed.
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

    void updateChartData(String k) {
        try {
            ArrayList<BarEntry> values = mStats.get(k).values;
            values.clear();

            if (isDailyChart()) {
                HashMap<Float, String> labels = new HashMap<>();

                Integer i = 0;
                Iterator<String> it = mJsonStats.keys();
                while (it.hasNext()) {
                    String d = it.next();
                    String count = "0";
                    if (mJsonStats.getJSONObject(d).has(k)) {
                        count = mJsonStats.getJSONObject(d).getJSONObject(k).getString("Sum");
                    }

                    values.add(i, new BarEntry(i, Integer.parseInt(count), d));
                    labels.put(i.floatValue(), d);
                    i++;
                }

                ((XAxisDailyValueFormatter) xavf).setLabels(labels);
            } else {
                Iterator<String> dit = mJsonStats.keys();
                while (dit.hasNext()) {
                    String d = dit.next();
                    if (mJsonStats.getJSONObject(d).optJSONObject("Hours") != null) {
                        mJsonHourStats = mJsonStats.getJSONObject(d).getJSONObject("Hours");

                        String h = "-1";

                        Iterator<String> it = mJsonHourStats.keys();
                        if (it.hasNext()) {
                            h = it.next();
                        }

                        for (int i = 0; i < 24; i++) {
                            String count = "0";

                            if (Integer.parseInt(h) == i) {
                                if (mJsonHourStats.getJSONObject(h).has(k) && mJsonHourStats.getJSONObject(h).getJSONObject(k).has("Sum")) {
                                    count = mJsonHourStats.getJSONObject(h).getJSONObject(k).getString("Sum");

                                    if (it.hasNext()) {
                                        h = it.next();
                                    }
                                }
                            }

                            float prevCount = 0;
                            try {
                                prevCount = values.get(i).getY();
                                values.remove(i);
                            } catch (Exception ignored) {
                            }
                            values.add(i, new BarEntry(i, prevCount + Integer.parseInt(count)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateChartData exception: " + e.toString());
        }
    }

    // Top lists
    void updateListsData(String k) {
        try {
            // Clear the stats first, in case there is no data
            mStats.get(k).total = "0";

            StatsKey2List k2ls = mStats.get(k).lists;
            String[] chartKeys = k2ls.keySet().toArray(new String[0]);

            for (String key : chartKeys) {
                k2ls.get(key).clear();
            }

            Integer count = 0;
            Iterator<String> it = mJsonStats.keys();
            while (it.hasNext()) {
                String d = it.next();
                if (mJsonStats.getJSONObject(d).has(k)) {
                    count += Integer.parseInt(mJsonStats.getJSONObject(d).getJSONObject(k).getString("Sum"));

                    JSONObject cks = mJsonStats.getJSONObject(d).getJSONObject(k);
                    for (String key : chartKeys) {
                        StatsList list = k2ls.get(key);

                        JSONObject vs = cks.getJSONObject(key);

                        JSONArray statsKeys = vs.names();
                        for (int i = 0; i < statsKeys.length(); i++) {
                            String sk = statsKeys.getString(i);
                            String v = vs.getString(sk);

                            int c = Integer.parseInt(v);
                            if (list.containsKey(sk)) {
                                c += list.get(sk);
                            }
                            list.put(sk, c);
                        }
                    }
                }
            }

            mStats.get(k).total = count.toString();

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateListsData exception: " + e.toString());
        }
    }

    void recreateStatsViews() {
        if (mIsLastChartDaily != isDailyChart()) {
            String[] keys = mCardViews.keySet().toArray(new String[0]);
            for (String k : keys) {
                mCardViews.get(k).removeAllViews();
            }

            createStatsViews();
            configureStatsViews();

            mIsLastChartDaily = isDailyChart();
        }
    }

    protected boolean isDailyChart() {
        return tvDaily.getText().toString().equals(getString(R.string.daily));
    }

    void setChartType(String type) {
        tvDaily.setText(type);
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                switch (id) {
                    case R.id.hourly:

                        setChartType(isDailyChart() ? getString(R.string.hourly) : getString(R.string.daily));
                        break;

                    case R.id.statsGeneralStats:
                    case R.id.statsRequestsByDate:

                        View cvGs = view.findViewById(R.id.cardviewGeneralStats);
                        int heightGs = cvGs.getHeight();

                        RelativeLayout.LayoutParams rlParamsGs = (RelativeLayout.LayoutParams) cvGs.getLayoutParams();
                        if (rlParamsGs.height == RelativeLayout.LayoutParams.WRAP_CONTENT) {
                            // XXX
                            rlParamsGs.height = heightGs < 332 ? heightGs : 332;
                        } else {
                            rlParamsGs.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        }

                        cvGs.setLayoutParams(rlParamsGs);

                        break;

                    case R.id.statsGeneralSrcIPTable:
                    case R.id.statsGeneralDstIPTable:
                    case R.id.statsGeneralDPortTable:
                    case R.id.statsGeneralTypeTable:

                        View cv = view.findViewById(R.id.cardviewAllStats);
                        int height = view.findViewById(R.id.cardviewGeneralStats).getHeight();

                        RelativeLayout.LayoutParams rlParams = (RelativeLayout.LayoutParams) cv.getLayoutParams();
                        if (rlParams.height == RelativeLayout.LayoutParams.WRAP_CONTENT) {
                            // XXX
                            rlParams.height = height < 332 ? height : 332;
                        } else {
                            rlParams.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                        }
                        cv.setLayoutParams(rlParams);

                        break;

                    default:
                        android.app.FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();
                        ((MainActivity) getActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
                        ((MainActivity) getActivity()).logFilePickerDialog.show(ft, "Selection Dialog");
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
            }
        }
    };
}

class StatsGeneralCache extends StatsCache {
    JSONObject mJsonAllStats;
    JSONObject mJsonBriefStats;
    JSONObject mJsonGeneralStats;
}
