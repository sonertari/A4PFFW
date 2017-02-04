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

public class LogsArchives extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RecyclerTouchListener.OnItemClickListener, LogFilePickerDialog.LogFilePickerDialogListener,
        ControllerTask.ControllerTaskListener {

    private View view;

    private LogsCache mModuleCache;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mLogsJsonArray;
    private final List<Log> mLogsList = new ArrayList<>();
    private LogRecyclerAdapter mAdapter;

    private TextView tvLogSize;
    private EditText etStartLine, etLinesPerPage, etRegex;

    private int mButton;
    private boolean mButtonPressed = false;
    private int mLinesPerPage, mLogSize = 0, mStartLine, mHeadStart;
    private String mRegex = "";

    private JSONObject mJsonLogFileList = new JSONObject();
    private String mLogFile = "";
    private String mSelectedLogFileOpt = "";

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.logs_archives, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        view.findViewById(R.id.logFile).setOnClickListener(mLabelClickedHandler);

        tvLogSize = (TextView) view.findViewById(R.id.logSize);

        etStartLine = (EditText) view.findViewById(R.id.startLine);
        etLinesPerPage = (EditText) view.findViewById(R.id.editTextLinesPerPage);
        etRegex = (EditText) view.findViewById(R.id.editTextRegex);

        view.findViewById(R.id.first).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.previous).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.next).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.last).setOnClickListener(mLabelClickedHandler);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mAdapter = new LogRecyclerAdapter(mLogsList);
        recyclerView.setAdapter(mAdapter);
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.logsArchive == null) {
            cache.logsArchive = new LogsCache();
        }
        mModuleCache = cache.logsArchive;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mLogsJsonArray = mLogsJsonArray;

        cache.mJsonLogFileList = mJsonLogFileList;

        mModuleCache.bundle.putString("mLogFile", mLogFile);
        mModuleCache.bundle.putString("mSelectedLogFileOpt", mSelectedLogFileOpt);

        mModuleCache.bundle.putInt("mLinesPerPage", mLinesPerPage);
        mModuleCache.bundle.putInt("mStartLine", mStartLine);
        mModuleCache.bundle.putInt("mLogSize", mLogSize);
        mModuleCache.bundle.putString("mRegex", mRegex);
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mLogsJsonArray = mModuleCache.mLogsJsonArray;

        if (mLogsJsonArray == null) {
            getLogs();
        } else {

            /// @todo Check why this does not work
            //if (mModuleCache.mLogsList != null) {
            //    mLogsList = mModuleCache.mLogsList;
            //    mAdapter.notifyDataSetChanged();
            //}

            mJsonLogFileList = cache.mJsonLogFileList;

            mLogFile = mModuleCache.bundle.getString("mLogFile");
            mSelectedLogFileOpt = mModuleCache.bundle.getString("mSelectedLogFileOpt");

            mLinesPerPage = mModuleCache.bundle.getInt("mLinesPerPage");
            mStartLine = mModuleCache.bundle.getInt("mStartLine");
            mLogSize = mModuleCache.bundle.getInt("mLogSize");
            mRegex = mModuleCache.bundle.getString("mRegex");

            updateSelections();
            updateLogs();
        }
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
            String output = controller.execute("pf", "SelectLogFile", mLogFile);

            mLogFile = new JSONArray(output).get(0).toString();

            output = controller.execute("pf", "GetFileLineCount", mLogFile, mRegex);

            mLogSize = new JSONArray(output).getInt(0);

            computeNavigationVars();

            String rules = controller.execute("pf", "GetLogs", mLogFile, mHeadStart, mLinesPerPage, mRegex);
            JSONArray jsonArray = new JSONArray(rules);
            mLogsJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("pf", "GetLogFilesList");

            mJsonLogFileList = new JSONObject(new JSONArray(output).get(0).toString());

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    @Override
    public void postExecute(boolean result) {
        ((MainActivity) getActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
        mSelectedLogFileOpt = ((MainActivity) getActivity()).logFilePickerDialog.updateLogFileLists();

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

    @Override
    public void onSelection(String selectedOpt, String fileName) {
        mSelectedLogFileOpt = selectedOpt;
        mLogFile = fileName;
        getLogs();
    }

    private void updateLogs() {

        try {
            mLogsList.clear();

            int i = 0;
            while (i < mLogsJsonArray.length()) {
                JSONObject logLine = mLogsJsonArray.getJSONObject(i);
                Log log = Log.newInstance(logLine, mStartLine + i + 1);
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
        tvLogSize.setText(String.format(Locale.getDefault(), "/ %1$d", mLogSize));

        /// @todo Check why setText() does not complain mLogSize is an int, then crashes at run-time
        // Apparently, an int can be confused with a char sequence
        //etStartLine.setText(mLogSize);
        etStartLine.setText(String.format(Locale.getDefault(), "%1$d", mStartLine + 1));
        etLinesPerPage.setText(String.format(Locale.getDefault(), "%1$d", mLinesPerPage));

        etRegex.setText(mRegex);

        ((TextView) view.findViewById(R.id.logFile)).setText(String.format(getString(R.string.log_file), mSelectedLogFileOpt));
    }

    private void getSelections() {
        try {
            mStartLine = Integer.parseInt(etStartLine.getText().toString()) - 1;
        } catch (Exception e) {
            mStartLine = 0;
        }
        try {
            mLinesPerPage = Math.min(999, Integer.parseInt(etLinesPerPage.getText().toString()));
        } catch (Exception e) {
            mLinesPerPage = 25;
        }
        mRegex = etRegex.getText().toString();
    }

    private void computeNavigationVars() {

        if (mButtonPressed) {
            if (mButton == R.id.first) {
                mStartLine = 0;
            } else if (mButton == R.id.previous) {
                mStartLine -= mLinesPerPage;
            } else if (mButton == R.id.next) {
                mStartLine += mLinesPerPage;
            } else if (mButton == R.id.last) {
                mStartLine = mLogSize;
            }
            mButtonPressed = false;
        }

        mHeadStart = mStartLine + mLinesPerPage;
        if (mHeadStart > mLogSize) {
            mHeadStart = mLogSize;
            mStartLine = mHeadStart - mLinesPerPage;
        }
        if (mStartLine < 0) {
            mStartLine = 0;
            mHeadStart = mLinesPerPage;
        }
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                if (id == R.id.logFile) {
                    android.app.FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();

                    ((MainActivity) getActivity()).logFilePickerDialog.setArguments(mLogFile, mJsonLogFileList);
                    ((MainActivity) getActivity()).logFilePickerDialog.show(ft, "Selection Dialog");
                } else {
                    mButtonPressed = true;
                    mButton = id;
                    getLogs();
                }

            } catch (Exception e) {
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onItemClick(View view) {
        TextView tvLog = (TextView) view.findViewById(R.id.log);
        TextView tvSrcDst = (TextView) view.findViewById(R.id.srcDst);

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

class Log {
    String num;
    String rule;
    String datetime;
    String action;
    String srcDst;
    String _if;
    String dir;
    String _type;
    String log;

    static Log newInstance(JSONObject logLine, int line) {
        Log log = new Log();
        try {
            log.num = Integer.toString(line);
            log.rule = logLine.getString("Rule");
            log.datetime = logLine.getString("Date") + ":" + logLine.getString("Time");
            log.action = logLine.getString("Act");
            log.dir = logLine.getString("Dir");
            log._if = logLine.getString("If");
            log.srcDst = logLine.getString("SrcIP") + ":" + logLine.getString("SPort") + " -> " + logLine.getString("DstIP") + ":" + logLine.getString("DPort");
            log._type = logLine.getString("Type");
            log.log = logLine.getString("Log");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Log newInstance exception: " + e.toString());
        }
        return log;
    }
}

class LogRecyclerAdapter extends RecyclerView.Adapter<LogRecyclerAdapter.LogViewHolder> {

    private final List<Log> logsList;

    class LogViewHolder extends RecyclerView.ViewHolder {
        public final TextView number;
        final TextView dirIf;
        final TextView srcDst;
        final TextView datetime;
        final TextView log;
        public final TextView image;


        LogViewHolder(View view) {
            super(view);
            number = (TextView) view.findViewById(R.id.number);
            dirIf = (TextView) view.findViewById(R.id.dirif);
            srcDst = (TextView) view.findViewById(R.id.srcDst);
            datetime = (TextView) view.findViewById(R.id.year);
            log = (TextView) view.findViewById(R.id.log);
            image = (TextView) view.findViewById(R.id.image);
        }
    }


    LogRecyclerAdapter(List<Log> list) {
        this.logsList = list;
    }

    @Override
    public LogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.log, parent, false);

        return new LogViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LogViewHolder holder, int position) {

        Log log = logsList.get(position);

        holder.number.setText(log.num);
        holder.dirIf.setText(String.format(holder.dirIf.getResources().getString(R.string.type_dir_if), log._type, log.dir, log._if));
        holder.srcDst.setText(log.srcDst);
        holder.datetime.setText(log.datetime);
        holder.log.setText(String.format(holder.log.getResources().getString(R.string.rule_log), log.rule, log.log));

        int image;
        String caption;

        if (log.action.compareTo("block") == 0) {
            image = R.drawable.block;
            caption = "B";
        } else if (log.action.compareTo("pass") == 0) {
            image = R.drawable.pass;
            caption = "P";
        } else {
            image = R.drawable.match;
            caption = "M";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return logsList.size();
    }
}

class LogsCache {
    public final Bundle bundle = new Bundle();

    JSONArray mLogsJsonArray;
}
