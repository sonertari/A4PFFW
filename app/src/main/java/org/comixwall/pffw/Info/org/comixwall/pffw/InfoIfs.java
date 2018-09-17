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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class InfoIfs extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoIfsCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mIfsJsonArray;
    private final List<If> mIfsList = new ArrayList<>();
    private IfRecyclerAdapter mIfsAdapter;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_ifs, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        RecyclerView rvIfs = view.findViewById(R.id.recyclerViewIfs);

        rvIfs.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvIfs.setItemAnimator(new DefaultItemAnimator());
        rvIfs.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mIfsAdapter = new IfRecyclerAdapter(mIfsList);
        rvIfs.setAdapter(mIfsAdapter);
        rvIfs.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.infoIfs == null) {
            cache.infoIfs = new InfoIfsCache();
        }
        mModuleCache = cache.infoIfs;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mIfsJsonArray = mIfsJsonArray;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;

        mIfsJsonArray = mModuleCache.mIfsJsonArray;

        if (mIfsJsonArray == null) {
            getInfo();
        } else {
            updateInfo();
        }

        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getInfo()
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
            String output = controller.execute("pf", "GetPfIfsInfo");

            JSONArray jsonArray = new JSONArray(output);
            mIfsJsonArray = new JSONArray(jsonArray.get(0).toString());

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
        updateIfList(mIfsJsonArray, mIfsList);
        mIfsAdapter.notifyDataSetChanged();
    }

    private void updateIfList(JSONArray jsonArray, List<If> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(If.newInstance(jsonArray.getJSONObject(i), i + 1));
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateIfList exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    @Override
    public void onItemClick(View view) {

        TextView tvName = view.findViewById(R.id.name);
        TextView tvCleared = view.findViewById(R.id.cleared);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvName.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvName.setMaxLines(lines);
        tvCleared.setMaxLines(lines);
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

class If {
    String num;
    public String name;
    String cleared;
    String states;
    String rules;
    String in4PassPackets;
    String in4PassBytes;
    String in4BlockPackets;
    String in4BlockBytes;
    String out4PassPackets;
    String out4PassBytes;
    String out4BlockPackets;
    String out4BlockBytes;
    String in6PassPackets;
    String in6PassBytes;
    String in6BlockPackets;
    String in6BlockBytes;
    String out6PassPackets;
    String out6PassBytes;
    String out6BlockPackets;
    String out6BlockBytes;

    static If newInstance(JSONObject ifEntry, int line) {
        If ifInfo = new If();
        try {
            ifInfo.num = Integer.toString(line);
            ifInfo.name = ifEntry.getString("name");
            ifInfo.cleared = ifEntry.getString("cleared");
            ifInfo.states = ifEntry.getString("states");
            ifInfo.rules = ifEntry.getString("rules");
            ifInfo.in4PassPackets = ifEntry.getString("in4PassPackets");
            ifInfo.in4PassBytes = ifEntry.getString("in4PassBytes");
            ifInfo.in4BlockPackets = ifEntry.getString("in4BlockPackets");
            ifInfo.in4BlockBytes = ifEntry.getString("in4BlockBytes");
            ifInfo.out4PassPackets = ifEntry.getString("out4PassPackets");
            ifInfo.out4PassBytes = ifEntry.getString("out4PassBytes");
            ifInfo.out4BlockPackets = ifEntry.getString("out4BlockPackets");
            ifInfo.out4BlockBytes = ifEntry.getString("out4BlockBytes");
            ifInfo.in6PassPackets = ifEntry.getString("in6PassPackets");
            ifInfo.in6PassBytes = ifEntry.getString("in6PassBytes");
            ifInfo.in6BlockPackets = ifEntry.getString("in6BlockPackets");
            ifInfo.in6BlockBytes = ifEntry.getString("in6BlockBytes");
            ifInfo.out6PassPackets = ifEntry.getString("out6PassPackets");
            ifInfo.out6PassBytes = ifEntry.getString("out6PassBytes");
            ifInfo.out6BlockPackets = ifEntry.getString("out6BlockPackets");
            ifInfo.out6BlockBytes = ifEntry.getString("out6BlockBytes");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("If newInstance exception: " + e.toString());
        }
        return ifInfo;
    }
}

class InfoIfsCache {
    JSONArray mIfsJsonArray;
}

class IfRecyclerAdapter extends RecyclerView.Adapter<IfRecyclerAdapter.IfViewHolder> {

    private final List<If> ifsList;

    class IfViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView number;
        final TextView statesRules;
        final TextView cleared;
        final TableLayout table;


        IfViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.name);
            number = view.findViewById(R.id.number);
            statesRules = view.findViewById(R.id.statesRules);
            cleared = view.findViewById(R.id.cleared);
            table = view.findViewById(R.id.table);
        }
    }


    IfRecyclerAdapter(List<If> list) {
        this.ifsList = list;
    }

    @Override
    public IfViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.ifinfo, parent, false);

        return new IfViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(IfViewHolder holder, int position) {

        If ifInfo = ifsList.get(position);

        holder.name.setText(ifInfo.name);
        holder.number.setText(ifInfo.num);
        holder.statesRules.setText(String.format(holder.statesRules.getResources().getString(R.string.states_rules), ifInfo.states, ifInfo.rules));
        holder.cleared.setText(String.format(holder.cleared.getResources().getString(R.string.cleared), ifInfo.cleared));

        holder.table.removeAllViews();

        TableRow row = (TableRow) LayoutInflater.from(holder.table.getContext()).inflate(R.layout.ifs_table_row, new TableRow(holder.table.getContext()), true);

        ((TextView) row.findViewById(R.id.rowHeader)).setText(R.string.in4);
        ((TextView) row.findViewById(R.id.passPackets)).setText(ifInfo.in4PassPackets);
        ((TextView) row.findViewById(R.id.passBytes)).setText(ifInfo.in4PassBytes);
        ((TextView) row.findViewById(R.id.blockPackets)).setText(ifInfo.in4BlockPackets);
        ((TextView) row.findViewById(R.id.blockBytes)).setText(ifInfo.in4BlockBytes);

        holder.table.addView(row);

        row = (TableRow) LayoutInflater.from(holder.table.getContext()).inflate(R.layout.ifs_table_row, new TableRow(holder.table.getContext()), true);

        ((TextView) row.findViewById(R.id.rowHeader)).setText(R.string.out4);
        ((TextView) row.findViewById(R.id.passPackets)).setText(ifInfo.out4PassPackets);
        ((TextView) row.findViewById(R.id.passBytes)).setText(ifInfo.out4PassBytes);
        ((TextView) row.findViewById(R.id.blockPackets)).setText(ifInfo.out4BlockPackets);
        ((TextView) row.findViewById(R.id.blockBytes)).setText(ifInfo.out4BlockBytes);

        holder.table.addView(row);

        row = (TableRow) LayoutInflater.from(holder.table.getContext()).inflate(R.layout.ifs_table_row, new TableRow(holder.table.getContext()), true);

        ((TextView) row.findViewById(R.id.rowHeader)).setText(R.string.in6);
        ((TextView) row.findViewById(R.id.passPackets)).setText(ifInfo.in6PassPackets);
        ((TextView) row.findViewById(R.id.passBytes)).setText(ifInfo.in6PassBytes);
        ((TextView) row.findViewById(R.id.blockPackets)).setText(ifInfo.in6BlockPackets);
        ((TextView) row.findViewById(R.id.blockBytes)).setText(ifInfo.in6BlockBytes);

        holder.table.addView(row);

        row = (TableRow) LayoutInflater.from(holder.table.getContext()).inflate(R.layout.ifs_table_row, new TableRow(holder.table.getContext()), true);

        ((TextView) row.findViewById(R.id.rowHeader)).setText(R.string.out6);
        ((TextView) row.findViewById(R.id.passPackets)).setText(ifInfo.out6PassPackets);
        ((TextView) row.findViewById(R.id.passBytes)).setText(ifInfo.out6PassBytes);
        ((TextView) row.findViewById(R.id.blockPackets)).setText(ifInfo.out6BlockPackets);
        ((TextView) row.findViewById(R.id.blockBytes)).setText(ifInfo.out6BlockBytes);

        holder.table.addView(row);
    }

    @Override
    public int getItemCount() {
        return ifsList.size();
    }
}
