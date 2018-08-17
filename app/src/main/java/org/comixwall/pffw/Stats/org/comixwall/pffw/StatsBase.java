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

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.test.espresso.core.deps.guava.collect.HashBiMap;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.Utils.showMessage;

/**
 * Base class for all states fragments.
 */
public abstract class StatsBase extends Fragment implements OnChartValueSelectedListener,
        SwipeRefreshLayout.OnRefreshListener, LogFilePickerDialog.LogFilePickerDialogListener,
        DatePickerDialog.OnDateSetListener, ControllerTask.ControllerTaskListener {

    // Type definitions
    class StatsList extends HashMap<String, Integer> {
    }

    class StatsKey2List extends HashMap<String, StatsList> {
    }

    class Stats {
        final String label;
        final Integer color;
        final BarChart chart;
        final ArrayList<BarEntry> values;
        String total;
        final TextView totalLabel;
        final StatsKey2List lists;
        final HashMap<String, TableLayout> statsTables;

        Stats(String label, Integer color, ArrayList<BarEntry> values, BarChart chart, String total, TextView totalLabel, StatsKey2List lists,
              HashMap<String, TableLayout> statsTables) {
            this.label = label;
            this.color = color;
            this.values = values;
            this.chart = chart;
            this.total = total;
            this.totalLabel = totalLabel;
            this.lists = lists;
            this.statsTables = statsTables;
        }
    }

    final HashMap<String, Stats> mStats = new HashMap<>();

    HashMap<String, CardView> mCardViews;

    private static HashMap<String, String> mChartLabels;

    private static final HashMap<String, Integer> mColors = new HashMap<String, Integer>() {{
        put("Total", Color.BLUE);
        put("Pass", Color.GREEN);
        put("Block", Color.RED);
        put("Match", Color.YELLOW);
    }};

    private static final ArrayList<String> statsKeys = new ArrayList<String>() {{
        add("SrcIP");
        add("DstIP");
        add("DPort");
        add("Type");
    }};

    /**
     * Comparator used to sort all of the top lists in reverse order.
     */
    final Comparator reverseComparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((Map.Entry<String, Integer>) o2).getValue().compareTo(((Map.Entry<String, Integer>) o1).getValue());
        }
    };

    IAxisValueFormatter xavf;
    IAxisValueFormatter yavf;

    static final HashMap<String, String> monthNames;
    static final HashMap<String, String> monthNumbers;

    static {
        monthNames = new HashMap<>();

        // ATTENTION: Do not translate month names, they are used to match the strings in log files in English
        monthNames.put("01", "Jan");
        monthNames.put("02", "Feb");
        monthNames.put("03", "Mar");
        monthNames.put("04", "Apr");
        monthNames.put("05", "May");
        monthNames.put("06", "Jun");
        monthNames.put("07", "Jul");
        monthNames.put("08", "Aug");
        monthNames.put("09", "Sep");
        monthNames.put("10", "Oct");
        monthNames.put("11", "Nov");
        monthNames.put("12", "Dec");

        monthNumbers = new HashMap<>(HashBiMap.create(monthNames).inverse());
    }

    View view;

    StatsCache mModuleCache;

    private SwipeRefreshLayout swipeRefresh;

    // ATTENTION: Init to "", not null, because we use empty string to fetch the default file
    String mLogFile = "";
    String mLastLogFile = "";

    private String mSelectedLogFileOpt = "";
    JSONObject mJsonLogFileList = new JSONObject();

    JSONObject mJsonStats;
    JSONObject mJsonHourStats = new JSONObject();

    String mMonth = "01";
    String mDay = "01";

    TextView tvMonthDay;

    String mLastError;

    void init() {
        mChartLabels = new HashMap<String, String>() {{
            put("Total", getString(R.string.all_requests));
            put("Pass", getString(R.string.allowed_requests));
            put("Block", getString(R.string.blocked_requests));
            put("Match", getString(R.string.matched_requests));
        }};

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        mCardViews = new HashMap<>();
        mCardViews.put("Total", (CardView) view.findViewById(R.id.cardviewTotal));
        mCardViews.put("Pass", (CardView) view.findViewById(R.id.cardviewPass));
        mCardViews.put("Block", (CardView) view.findViewById(R.id.cardviewBlock));
        mCardViews.put("Match", (CardView) view.findViewById(R.id.cardviewMatch));
    }

    @Override
    public void onResume() {
        super.onResume();

        restoreState();
    }

    @Override
    public void onPause() {
        super.onPause();

        saveState();
    }

    protected abstract void restoreState();

    protected abstract void saveState();

    void restoreBaseState() {
        mJsonLogFileList = cache.mJsonLogFileList;

        mLogFile = mModuleCache.bundle.getString("mLogFile");
        mLastLogFile = mModuleCache.bundle.getString("mLastLogFile");

        mSelectedLogFileOpt = mModuleCache.bundle.getString("mSelectedLogFileOpt");
        mMonth = mModuleCache.bundle.getString("mMonth");
        mDay = mModuleCache.bundle.getString("mDay");
    }

    void saveBaseState() {
        cache.mJsonLogFileList = mJsonLogFileList;

        mModuleCache.mJsonStats = mJsonStats;
        mModuleCache.bundle.putString("mLogFile", mLogFile);
        mModuleCache.bundle.putString("mLastLogFile", mLastLogFile);

        mModuleCache.bundle.putString("mSelectedLogFileOpt", mSelectedLogFileOpt);
        mModuleCache.bundle.putString("mMonth", mMonth);
        mModuleCache.bundle.putString("mDay", mDay);
    }

    void createStatsViews() {
        String[] keys = mCardViews.keySet().toArray(new String[0]);
        for (String k : keys) {
            getActivity().getLayoutInflater().inflate(R.layout.stats_cardview, mCardViews.get(k), true);
        }
    }

    void configureStatsViews() {
        if (isDailyChart()) {
            xavf = new XAxisDailyValueFormatter();
        } else {
            xavf = new AxisValueFormatter();
        }

        yavf = new AxisValueFormatter();

        String[] keys = mCardViews.keySet().toArray(new String[0]);
        for (String k : keys) {

            CardView cv = mCardViews.get(k);

            if (isDailyChart()) {
                configureHorizontalBarChart(k);
            } else {
                configureBarChart(k);
            }

            ((TextView) cv.findViewById(R.id.chartLabel)).setText(mChartLabels.get(k));

            StatsKey2List statsLists = new StatsKey2List();
            statsLists.put("SrcIP", new StatsList());
            statsLists.put("DstIP", new StatsList());
            statsLists.put("DPort", new StatsList());
            statsLists.put("Type", new StatsList());

            HashMap<String, TableLayout> statsTables = new HashMap<>();
            statsTables.put("SrcIP", (TableLayout) cv.findViewById(R.id.statsSrcIPTable));
            statsTables.put("DstIP", (TableLayout) cv.findViewById(R.id.statsDstIPTable));
            statsTables.put("DPort", (TableLayout) cv.findViewById(R.id.statsDPortTable));
            statsTables.put("Type", (TableLayout) cv.findViewById(R.id.statsTypeTable));

            mStats.put(k, new Stats(
                    mChartLabels.get(k),
                    mColors.get(k),
                    new ArrayList<BarEntry>(),
                    (BarChart) cv.findViewById(R.id.chart),
                    "0",
                    (TextView) cv.findViewById(R.id.chartTotal),
                    statsLists,
                    statsTables
            ));
        }
    }

    @Override
    public void executePreTask() {
    }

    @Override
    public void preExecute() {
        swipeRefresh.setRefreshing(true);
    }

    @Override
    public boolean executeTask() {
        return fetchStats();
    }

    @Override
    public void postExecute(boolean result) {
        ((MainActivity) getActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
        mSelectedLogFileOpt = ((MainActivity) getActivity()).logFilePickerDialog.updateLogFileLists();

        updateLogFileText();
        updateDateTimeText();

        if (result) {
            updateStats();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    protected abstract boolean isDailyChart();

    protected abstract void configureHorizontalBarChart(String k);

    private void configureBarChart(String k) {

        CardView cv = mCardViews.get(k);

        BarChart chart = (BarChart) cv.findViewById(R.id.chart);
        chart.setOnChartValueSelectedListener(this);
        chart.setDrawValueAboveBar(true);
        chart.setDrawBarShadow(false);
        chart.getDescription().setEnabled(false);
        // scaling can now only be done on x- and y-axis separately
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setMaxVisibleValueCount(8);
        chart.animateY(1000);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f); // only intervals of 1
        xAxis.setLabelCount(7);
        xAxis.setValueFormatter(xavf);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setLabelCount(8, false);
        leftAxis.setValueFormatter(yavf);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setSpaceTop(15f);
        leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        Legend l = chart.getLegend();
        l.setEnabled(false);

        XYMarkerView mv = new XYMarkerView(view.getContext(), xavf);
        mv.setChartView(chart); // For bounds control
        chart.setMarker(mv); // Set the marker to the chart
    }

    @Override
    public void onRefresh() {
        getStats();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getStats();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void getStats() {
        ControllerTask.run(this, this);
    }

    protected abstract boolean fetchStats();

    String formatDate() {
        return monthNames.get(mMonth) + " " + mDay;
    }

    void updateDateTimeText() {
    }

    void updateLogFileText() {
        ((TextView) view.findViewById(R.id.logFile)).setText(String.format(getResources().getString(R.string.log_file), mSelectedLogFileOpt));
    }

    protected abstract void updateStats();

    void updateChart(String key) {

        ArrayList<BarEntry> values = mStats.get(key).values;

        BarDataSet set1;
        BarChart chart = mStats.get(key).chart;

        if (chart.getData() != null && chart.getData().getDataSetCount() > 0) {
            set1 = (BarDataSet) chart.getData().getDataSetByIndex(0);
            set1.setValues(values);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
        } else {
            set1 = new BarDataSet(values, key);
            set1.setColor(mStats.get(key).color);

            ArrayList<IBarDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1);

            BarData data = new BarData(dataSets);
            data.setValueTextSize(10f);
            data.setBarWidth(0.9f);
            IValueFormatter f = new IValueFormatter() {
                @Override
                public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                    DecimalFormat mFormat = new DecimalFormat("###,###,###,##0");
                    return mFormat.format(value);
                }
            };
            data.setValueFormatter(f);

            chart.setData(data);
        }

        mStats.get(key).chart.invalidate();
    }

    void updateLists(String key) {

        mStats.get(key).totalLabel.setText(String.format(getResources().getString(R.string.total_smallcaps), mStats.get(key).total));

        for (String k : statsKeys) {
            TableLayout statsTable = mStats.get(key).statsTables.get(k);
            statsTable.removeAllViews();

            Object[] kvps = mStats.get(key).lists.get(k).entrySet().toArray();
            Arrays.sort(kvps, reverseComparator);

            int count = 1;
            for (Object entry : kvps) {
                TableRow row = (TableRow) getActivity().getLayoutInflater().inflate(R.layout.stats_table_row, new TableRow(this.view.getContext()), true);

                ((TextView) row.findViewById(R.id.tableValue)).setText(((Map.Entry<String, Integer>) entry).getValue().toString());
                ((TextView) row.findViewById(R.id.tableKey)).setText(((Map.Entry<String, Integer>) entry).getKey());
                statsTable.addView(row);

                if (++count > 10) {
                    break;
                }
            }
        }
    }

    boolean isLogFileChanged() {
        return !mLogFile.equals(mLastLogFile);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        // ATTENTION: month is 0 based, so add one
        mMonth = String.format("%02d", month + 1);
        mDay = String.format("%02d", dayOfMonth);
        updateDateTimeText();
    }

    @Override
    public void onSelection(String selectedOpt, String fileName) {
        mSelectedLogFileOpt = selectedOpt;
        mLogFile = fileName;
        getStats();
    }

    private final RectF mOnValueSelectedRectF = new RectF();

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null)
            return;

        // All charts have the same id
        BarChart chart = (BarChart) view.findViewById(R.id.chart);

        chart.getBarBounds((BarEntry) e, mOnValueSelectedRectF);
        MPPointF position = chart.getPosition(e, YAxis.AxisDependency.LEFT);
        MPPointF.recycleInstance(position);
    }

    @Override
    public void onNothingSelected() {
    }
}

class StatsCache {
    public final Bundle bundle = new Bundle();

    JSONObject mJsonStats;
}
