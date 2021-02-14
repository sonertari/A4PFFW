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

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class LogsLive extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RecyclerTouchListener.OnItemClickListener, RefreshTimer.OnTimeoutListener,
        ControllerTask.ControllerTaskListener {

    private LogsCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mLogsJsonArray;
    private final List<Log> mLogsList = new ArrayList<>();
    private LogRecyclerAdapter mAdapter;

    private EditText etLinesPerPage, etRegex;
    private int mLinesPerPage, mLogSize;
    private String mRegex = "";

    private JSONObject mJsonLogFileList = new JSONObject();
    private String mLogFile = "";

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.logs_live, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        etLinesPerPage = view.findViewById(R.id.editTextLinesPerPage);
        etRegex = view.findViewById(R.id.editTextRegex);

        RecyclerView mRecyclerView = view.findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mAdapter = new LogRecyclerAdapter(mLogsList);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.logsLive == null) {
            cache.logsLive = new LogsCache();
        }
        mModuleCache = cache.logsLive;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mLogsJsonArray = mLogsJsonArray;

        cache.mJsonLogFileList = mJsonLogFileList;

        mModuleCache.bundle.putString("mLogFile", mLogFile);

        mModuleCache.bundle.putInt("mLinesPerPage", mLinesPerPage);
        mModuleCache.bundle.putInt("mLogSize", mLogSize);
        mModuleCache.bundle.putString("mRegex", mRegex);

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mLogsJsonArray = mModuleCache.mLogsJsonArray;

        if (mLogsJsonArray == null) {
            getLogs();
        } else {

            // TODO: Check why this does not work
            //if (mModuleCache.mLogsList != null) {
            //    mLogsList = mModuleCache.mLogsList;
            //    mAdapter.notifyDataSetChanged();
            //}

            mJsonLogFileList = cache.mJsonLogFileList;

            mLogFile = mModuleCache.bundle.getString("mLogFile");

            mLinesPerPage = mModuleCache.bundle.getInt("mLinesPerPage");
            mLogSize = mModuleCache.bundle.getInt("mLogSize");
            mRegex = mModuleCache.bundle.getString("mRegex");

            updateSelections();
            updateLogs();
        }

        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getLogs()
        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        mTimer.start(mRefreshTimeout);
    }

    @Override
    public void onTimeout() {
        getLogs();
    }

    @Override
    public void executePreTask() {
        getSelections();
    }

    @Override
    public void preExecute() {
        swipeRefresh.setRefreshing(true);
    }

    @Override
    public boolean executeTask() {
        try {
            String output = controller.execute("pf", "GetDefaultLogFile");

            mLogFile = new JSONArray(output).get(0).toString();

            output = controller.execute("pf", "GetFileLineCount", mLogFile, mRegex);

            mLogSize = new JSONArray(output).getInt(0);

            output = controller.execute("pf", "GetLiveLogs", mLogFile, mLinesPerPage, mRegex);

            JSONArray jsonArray = new JSONArray(output);
            mLogsJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("pf", "GetReloadRate");

            int timeout = Integer.parseInt(new JSONArray(output).get(0).toString());
            mRefreshTimeout = timeout < 10 ? 10 : timeout;

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    @Override
    public void postExecute(boolean result) {
        updateSelections();

        if (result) {
            updateLogs();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    private void getLogs() {
        ControllerTask.run(this, this);
    }

    private void updateLogs() {

        try {
            int lineCount = 1;
            if (mLogSize > mLinesPerPage) {
                lineCount = mLogSize - mLinesPerPage + 1;
            }

            mLogsList.clear();

            int i = 0;
            while (i < mLogsJsonArray.length()) {
                JSONObject logLine = mLogsJsonArray.getJSONObject(i);
                Log log = Log.newInstance(logLine, lineCount + i);
                mLogsList.add(log);
                i++;
            }

            mAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateLogs exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getLogs();
    }

    private void updateSelections() {
        etLinesPerPage.setText(String.format(Locale.getDefault(), "%1$d", mLinesPerPage));
        etRegex.setText(mRegex);
    }

    private void getSelections() {
        try {
            mLinesPerPage = Math.min(999, Integer.parseInt(etLinesPerPage.getText().toString()));
        } catch (Exception e) {
            mLinesPerPage = 25;
        }
        mRegex = etRegex.getText().toString();
    }

    @Override
    public void onItemClick(View view) {
        TextView tvLog = view.findViewById(R.id.log);
        TextView tvSrcDst = view.findViewById(R.id.srcDst);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvLog.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvLog.setMaxLines(lines);
        tvSrcDst.setMaxLines(lines);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getLogs();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
