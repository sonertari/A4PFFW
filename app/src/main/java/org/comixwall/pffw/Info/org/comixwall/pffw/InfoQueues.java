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
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class InfoQueues extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoQueuesCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mQueuesJsonArray;
    private final List<Queue> mQueuesList = new ArrayList<>();
    private QueueRecyclerAdapter mQueuesAdapter;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_queues, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        RecyclerView rvQueues = view.findViewById(R.id.recyclerViewQueues);

        rvQueues.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvQueues.setItemAnimator(new DefaultItemAnimator());
        rvQueues.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mQueuesAdapter = new QueueRecyclerAdapter(mQueuesList);
        rvQueues.setAdapter(mQueuesAdapter);
        rvQueues.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.infoQueues == null) {
            cache.infoQueues = new InfoQueuesCache();
        }
        mModuleCache = cache.infoQueues;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mQueuesJsonArray = mQueuesJsonArray;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;

        mQueuesJsonArray = mModuleCache.mQueuesJsonArray;

        if (mQueuesJsonArray == null) {
            getInfo();
        } else {
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
            String output = controller.execute("pf", "GetPfQueueInfo");

            JSONArray jsonArray = new JSONArray(output);
            mQueuesJsonArray = new JSONArray(jsonArray.get(0).toString());

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
        updateQueueList(mQueuesJsonArray, mQueuesList);
        mQueuesAdapter.notifyDataSetChanged();
    }

    private void updateQueueList(JSONArray jsonArray, List<Queue> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(Queue.newInstance(jsonArray.getJSONObject(i), i + 1));
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateQueueList exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    @Override
    public void onItemClick(View view) {

        TextView tvPacketsBytes = view.findViewById(R.id.packetsBytes);
        TextView tvDropped = view.findViewById(R.id.dropped);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvPacketsBytes.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvPacketsBytes.setMaxLines(lines);
        tvDropped.setMaxLines(lines);
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

class Queue {
    String num;
    public String name;
    String pkts;
    String bytes;
    String droppedPkts;
    String droppedBytes;
    String len;

    static Queue newInstance(JSONObject qEntry, int line) {
        Queue queue = new Queue();
        try {
            queue.num = Integer.toString(line);
            queue.name = qEntry.getString("name");
            queue.pkts = qEntry.getString("pkts");
            queue.bytes = qEntry.getString("bytes");
            queue.droppedPkts = qEntry.getString("droppedPkts");
            queue.droppedBytes = qEntry.getString("droppedBytes");
            queue.len = qEntry.getString("length");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Queue newInstance exception: " + e.toString());
        }
        return queue;
    }
}

class QueueRecyclerAdapter extends RecyclerView.Adapter<QueueRecyclerAdapter.QueueViewHolder> {

    private final List<Queue> queueList;

    class QueueViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView packetsBytes;
        final TextView dropped;
        final TextView number;
        final TextView len;
        final TextView image;


        QueueViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.name);
            packetsBytes = view.findViewById(R.id.packetsBytes);
            dropped = view.findViewById(R.id.dropped);
            number = view.findViewById(R.id.number);
            len = view.findViewById(R.id.len);
            image = view.findViewById(R.id.image);
        }
    }

    QueueRecyclerAdapter(List<Queue> list) {
        this.queueList = list;
    }

    @Override
    public QueueViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.queue, parent, false);

        return new QueueViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(QueueViewHolder holder, int position) {

        Queue queue = queueList.get(position);

        holder.name.setText(queue.name);
        holder.packetsBytes.setText(String.format(holder.packetsBytes.getResources().getString(R.string.packets_bytes), queue.pkts, queue.bytes));
        holder.number.setText(queue.num);
        holder.dropped.setText(String.format(holder.dropped.getResources().getString(R.string.dropped_packets_bytes), queue.droppedPkts, queue.droppedBytes));
        holder.len.setText(String.format(holder.len.getResources().getString(R.string.length), queue.len));

        // 0/50
        float used = 0f;
        float len = 1f;
        Pattern p = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Matcher m = p.matcher(queue.len);
        if (m.matches()) {
            used = Float.parseFloat(m.group(1));
            len = Float.parseFloat(m.group(2));
        }

        float ratio = used / len;

        int image;
        String caption;

        if (ratio > 0.66f) {
            image = R.drawable.block;
            caption = "H";
        } else if (ratio > 0.33f) {
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
        return queueList.size();
    }
}

class InfoQueuesCache {
    JSONArray mQueuesJsonArray;
}
