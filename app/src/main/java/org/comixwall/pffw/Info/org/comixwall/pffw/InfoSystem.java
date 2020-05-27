/*
 * Copyright (C) 2017-2020 Soner Tari
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
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class InfoSystem extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoSystemCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private String mSymonStatus;
    private String mSymuxStatus;

    private TextView tvSymonStatus;
    private TextView tvSymuxStatus;

    private ImageView ivSymonStatus;
    private ImageView ivSymuxStatus;

    private JSONArray mSymonJsonArray;
    private final List<Process> mSymonList = new ArrayList<>();
    private ProcessRecyclerAdapter mSymonAdapter;

    private JSONArray mSymuxJsonArray;
    private final List<Process> mSymuxList = new ArrayList<>();
    private ProcessRecyclerAdapter mSymuxAdapter;

    private JSONArray mSystemJsonArray;
    private final List<Process> mSystemList = new ArrayList<>();
    private ProcessRecyclerAdapter mSystemAdapter;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_system, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        tvSymonStatus = view.findViewById(R.id.symonStatus);
        tvSymuxStatus = view.findViewById(R.id.symuxStatus);

        ivSymonStatus = view.findViewById(R.id.imageViewSymonStatus);
        ivSymuxStatus = view.findViewById(R.id.imageViewSymuxStatus);

        RecyclerView rvSymon = view.findViewById(R.id.recyclerViewSymon);
        rvSymon.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSymon.setItemAnimator(new DefaultItemAnimator());
        rvSymon.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mSymonAdapter = new ProcessRecyclerAdapter(mSymonList);
        rvSymon.setAdapter(mSymonAdapter);
        rvSymon.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        RecyclerView rvSymux = view.findViewById(R.id.recyclerViewSymux);
        rvSymux.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSymux.setItemAnimator(new DefaultItemAnimator());
        rvSymux.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mSymuxAdapter = new ProcessRecyclerAdapter(mSymuxList);
        rvSymux.setAdapter(mSymuxAdapter);
        rvSymux.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        RecyclerView rvSystem = view.findViewById(R.id.recyclerViewSystem);
        rvSystem.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvSystem.setItemAnimator(new DefaultItemAnimator());
        rvSystem.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mSystemAdapter = new ProcessRecyclerAdapter(mSystemList);
        rvSystem.setAdapter(mSystemAdapter);
        rvSystem.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.infoSystem == null) {
            cache.infoSystem = new InfoSystemCache();
        }
        mModuleCache = cache.infoSystem;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.bundle.putString("mSymonStatus", mSymonStatus);
        mModuleCache.bundle.putString("mSymuxStatus", mSymuxStatus);

        mModuleCache.mSymonJsonArray = mSymonJsonArray;
        mModuleCache.mSymuxJsonArray = mSymuxJsonArray;
        mModuleCache.mSystemJsonArray = mSystemJsonArray;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mSymonStatus = mModuleCache.bundle.getString("mSymonStatus");

        if (mSymonStatus == null) {
            getInfo();
        } else {
            mSymuxStatus = mModuleCache.bundle.getString("mSymuxStatus");

            mSymonJsonArray = mModuleCache.mSymonJsonArray;
            mSymuxJsonArray = mModuleCache.mSymuxJsonArray;
            mSystemJsonArray = mModuleCache.mSystemJsonArray;

            updateInfo();
        }

        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        mTimer.start(mRefreshTimeout);
    }

    @Override
    public void onTimeout() {
        getInfo();
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
        Boolean retval = true;
        try {
            String output = controller.execute("symon", "IsRunning");

            mSymonStatus = new JSONArray(output).get(2).toString();

            output = controller.execute("symon", "GetProcList");

            JSONArray jsonArray = new JSONArray(output);
            mSymonJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("symux", "IsRunning");

            mSymuxStatus = new JSONArray(output).get(2).toString();

            output = controller.execute("symux", "GetProcList");

            jsonArray = new JSONArray(output);
            mSymuxJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("system", "GetProcList");

            jsonArray = new JSONArray(output);
            mSystemJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("pf", "GetReloadRate");

            int timeout = Integer.parseInt(new JSONArray(output).get(0).toString());
            mRefreshTimeout = timeout < 10 ? 10 : timeout;

        } catch (Exception e) {
            mLastError = processException(e);
            retval = false;
        }
        return retval;
    }

    @Override
    public void postExecute(boolean result) {
        if (result) {
            updateInfo();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    private void getInfo() {
        ControllerTask.run(this, this);
    }

    private void updateInfo() {

        Utils.updateStatusViews(mSymonStatus, ivSymonStatus, tvSymonStatus, "Symon");

        updateProcList(mSymonJsonArray, mSymonList);
        mSymonAdapter.notifyDataSetChanged();

        Utils.updateStatusViews(mSymuxStatus, ivSymuxStatus, tvSymuxStatus, "Symux");

        updateProcList(mSymuxJsonArray, mSymuxList);
        mSymuxAdapter.notifyDataSetChanged();

        updateProcList(mSystemJsonArray, mSystemList);
        mSystemAdapter.notifyDataSetChanged();
    }

    private void updateProcList(JSONArray jsonArray, List<Process> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(Process.newInstance(jsonArray.getJSONArray(i++)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateProcList exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    @Override
    public void onItemClick(View view) {
        TextView tvCommand = view.findViewById(R.id.command);
        TextView tvOthers = view.findViewById(R.id.others);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvCommand.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvCommand.setMaxLines(lines);
        tvOthers.setMaxLines(lines);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getInfo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

class Process {
    String pid;
    String started;
    String cpu;
    public String time;
    String mem;
    String rss;
    String vsz;
    String stat;
    String pri;
    String ni;
    String user;
    String group;
    String command;

    static Process newInstance(JSONArray procLine) {
        Process proc = new Process();
        try {
            proc.pid = procLine.getString(0);
            proc.started = procLine.getString(1);
            proc.cpu = procLine.getString(2);
            proc.time = procLine.getString(3);
            proc.mem = procLine.getString(4);
            proc.rss = procLine.getString(5);
            proc.vsz = procLine.getString(6);
            proc.stat = procLine.getString(7);
            proc.pri = procLine.getString(8);
            proc.ni = procLine.getString(9);
            proc.user = procLine.getString(10);
            proc.group = procLine.getString(11);
            proc.command = procLine.getString(12);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Process newInstance exception: " + e.toString());
        }
        return proc;
    }
}

class ProcessRecyclerAdapter extends RecyclerView.Adapter<ProcessRecyclerAdapter.ProcessViewHolder> {

    private final List<Process> procsList;

    class ProcessViewHolder extends RecyclerView.ViewHolder {
        final TextView cpuMemTime;
        final TextView command;
        final TextView pid;
        final TextView others;
        final TextView started;
        final TextView image;


        ProcessViewHolder(View view) {
            super(view);
            cpuMemTime = view.findViewById(R.id.cpuMemTime);
            command = view.findViewById(R.id.command);
            pid = view.findViewById(R.id.pid);
            others = view.findViewById(R.id.others);
            started = view.findViewById(R.id.started);
            image = view.findViewById(R.id.image);
        }
    }

    ProcessRecyclerAdapter(List<Process> list) {
        this.procsList = list;
    }

    @Override
    public ProcessViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.process, parent, false);

        return new ProcessViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ProcessViewHolder holder, int position) {

        Process proc = procsList.get(position);

        holder.cpuMemTime.setText(String.format(holder.cpuMemTime.getResources().getString(R.string.cpu_mem_time), proc.cpu, proc.mem, proc.time));
        holder.command.setText(proc.command);
        holder.pid.setText(proc.pid);
        holder.others.setText("rss: " + proc.rss + ", vsz: " + proc.vsz + ", " + proc.user + ":" + proc.group + ", stat: " + proc.stat + ", pri: " + proc.pri + ", ni: " + proc.ni);
        holder.started.setText(proc.started);

        int pri = Integer.parseInt(proc.pri);

        int image;
        String caption;

        if (pri > 20) {
            image = R.drawable.block;
            caption = "H";
        } else if (pri > 10) {
            image = R.drawable.pass;
            caption = "M";
        } else {
            image = R.drawable.match;
            caption = "L";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return procsList.size();
    }
}

class InfoSystemCache {
    public final Bundle bundle = new Bundle();

    JSONArray mSymonJsonArray;
    JSONArray mSymuxJsonArray;
    JSONArray mSystemJsonArray;
}
