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
import android.widget.ImageView;
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

public class InfoHosts extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoHostsCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private String mDhcpdStatus;
    private String mNamedStatus;

    private TextView tvDhcpdStatus;
    private TextView tvNamedStatus;

    private ImageView ivDhcpdStatus;
    private ImageView ivNamedStatus;

    private JSONArray mDhcpdJsonArray;
    private final List<Process> mDhcpdList = new ArrayList<>();
    private ProcessRecyclerAdapter mDhcpdAdapter;

    private JSONArray mNamedJsonArray;
    private final List<Process> mNamedList = new ArrayList<>();
    private ProcessRecyclerAdapter mNamedAdapter;

    private JSONArray mArpTableJsonArray;
    private final List<Arp> mArpTableList = new ArrayList<>();
    private ArpTableRecyclerAdapter mArpTableAdapter;

    private JSONArray mLeasesJsonArray;
    private final List<Lease> mLeasesList = new ArrayList<>();
    private LeaseRecyclerAdapter mLeasesAdapter;

    private final RecyclerTouchListener.OnItemClickListener mArpTableItemClickListener = new RecyclerTouchListener.OnItemClickListener() {
        @Override
        public void onItemClick(View view) {
            TextView tvIp = (TextView) view.findViewById(R.id.ip);
            TextView tvMac = (TextView) view.findViewById(R.id.mac);

            int lines = 10;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (tvIp.getMaxLines() != 1) {
                    lines = 1;
                }
            }

            tvIp.setMaxLines(lines);
            tvMac.setMaxLines(lines);
        }
    };

    private final RecyclerTouchListener.OnItemClickListener mLeasesItemClickListener = new RecyclerTouchListener.OnItemClickListener() {
        @Override
        public void onItemClick(View view) {
            TextView tvStartEnd = (TextView) view.findViewById(R.id.startEnd);
            TextView tvIp = (TextView) view.findViewById(R.id.ip);
            TextView tvHost = (TextView) view.findViewById(R.id.host);

            int lines = 10;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (tvIp.getMaxLines() != 1) {
                    lines = 1;
                }
            }

            tvStartEnd.setMaxLines(lines);
            tvIp.setMaxLines(lines);
            tvHost.setMaxLines(lines);
        }
    };

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_hosts, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        tvDhcpdStatus = (TextView) view.findViewById(R.id.dhcpdStatus);
        tvNamedStatus = (TextView) view.findViewById(R.id.namedStatus);

        ivDhcpdStatus = (ImageView) view.findViewById(R.id.imageViewDhcpdStatus);
        ivNamedStatus = (ImageView) view.findViewById(R.id.imageViewNamedStatus);

        RecyclerView rvDhcpd = (RecyclerView) view.findViewById(R.id.recyclerViewDhcpd);
        // ATTENTION: Should use separate LayoutManager for each RecyclerView.
        rvDhcpd.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvDhcpd.setItemAnimator(new DefaultItemAnimator());
        rvDhcpd.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mDhcpdAdapter = new ProcessRecyclerAdapter(mDhcpdList);
        rvDhcpd.setAdapter(mDhcpdAdapter);
        rvDhcpd.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        RecyclerView rvNamed = (RecyclerView) view.findViewById(R.id.recyclerViewNamed);
        rvNamed.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvNamed.setItemAnimator(new DefaultItemAnimator());
        rvNamed.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mNamedAdapter = new ProcessRecyclerAdapter(mNamedList);
        rvNamed.setAdapter(mNamedAdapter);
        rvNamed.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        RecyclerView rvArpTable = (RecyclerView) view.findViewById(R.id.recyclerViewArpTable);
        rvArpTable.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvArpTable.setItemAnimator(new DefaultItemAnimator());
        rvArpTable.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mArpTableAdapter = new ArpTableRecyclerAdapter(mArpTableList);
        rvArpTable.setAdapter(mArpTableAdapter);
        rvArpTable.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), mArpTableItemClickListener));

        RecyclerView rvLeases = (RecyclerView) view.findViewById(R.id.recyclerViewLeases);
        rvLeases.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvLeases.setItemAnimator(new DefaultItemAnimator());
        rvLeases.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mLeasesAdapter = new LeaseRecyclerAdapter(mLeasesList);
        rvLeases.setAdapter(mLeasesAdapter);
        rvLeases.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), mLeasesItemClickListener));

        if (cache.infoHosts == null) {
            cache.infoHosts = new InfoHostsCache();
        }
        mModuleCache = cache.infoHosts;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.bundle.putString("mDhcpdStatus", mDhcpdStatus);
        mModuleCache.bundle.putString("mNamedStatus", mNamedStatus);

        mModuleCache.mDhcpdJsonArray = mDhcpdJsonArray;
        mModuleCache.mNamedJsonArray = mNamedJsonArray;
        mModuleCache.mArpTableJsonArray = mArpTableJsonArray;
        mModuleCache.mLeasesJsonArray = mLeasesJsonArray;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mDhcpdStatus = mModuleCache.bundle.getString("mDhcpdStatus");

        if (mDhcpdStatus == null) {
            getInfo();
        } else {

            mNamedStatus = mModuleCache.bundle.getString("mNamedStatus");

            mDhcpdJsonArray = mModuleCache.mDhcpdJsonArray;
            mNamedJsonArray = mModuleCache.mNamedJsonArray;
            mArpTableJsonArray = mModuleCache.mArpTableJsonArray;
            mLeasesJsonArray = mModuleCache.mLeasesJsonArray;

            updateInfo();
        }

        // ATTENTION: Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getInfo()
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

    /**
     * Fetch dhcpd and named status, arp table and dhcpd leases.
     *
     * @return True on success, false on failure.
     */
    @Override
    public boolean executeTask() {
        Boolean retval = true;
        try {
            String output = controller.execute("dhcpd", "IsRunning");
            mDhcpdStatus = new JSONArray(output).get(2).toString();

            output = controller.execute("dhcpd", "GetProcList");
            JSONArray jsonArray = new JSONArray(output);
            mDhcpdJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("named", "IsRunning");
            mNamedStatus = new JSONArray(output).get(2).toString();

            output = controller.execute("named", "GetProcList");
            jsonArray = new JSONArray(output);
            mNamedJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("dhcpd", "GetArpTable");
            jsonArray = new JSONArray(output);
            mArpTableJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("dhcpd", "GetLeases");
            jsonArray = new JSONArray(output);
            mLeasesJsonArray = new JSONArray(jsonArray.get(0).toString());

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

        Utils.updateStatusViews(mDhcpdStatus, ivDhcpdStatus, tvDhcpdStatus, getString(R.string.dhcp_server));

        updateProcList(mDhcpdJsonArray, mDhcpdList);
        mDhcpdAdapter.notifyDataSetChanged();

        Utils.updateStatusViews(mNamedStatus, ivNamedStatus, tvNamedStatus, getString(R.string.dns_server));

        updateProcList(mNamedJsonArray, mNamedList);
        mNamedAdapter.notifyDataSetChanged();

        updateArpList(mArpTableJsonArray, mArpTableList);
        mArpTableAdapter.notifyDataSetChanged();

        updateLeaseList(mLeasesJsonArray, mLeasesList);
        mLeasesAdapter.notifyDataSetChanged();
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

    private void updateArpList(JSONArray jsonArray, List<Arp> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(Arp.newInstance(jsonArray.getJSONObject(i), i + 1));
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateArpList exception: " + e.toString());
        }
    }

    private void updateLeaseList(JSONArray jsonArray, List<Lease> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(Lease.newInstance(jsonArray.getJSONObject(i), i + 1));
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateLeaseList exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    /**
     * Change the size of certain text views on touch if the content does not fit.
     *
     * @param view Container view
     */
    @Override
    public void onItemClick(View view) {
        TextView tvCommand = (TextView) view.findViewById(R.id.command);
        TextView tvUserGroup = (TextView) view.findViewById(R.id.others);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvCommand.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvCommand.setMaxLines(lines);
        tvUserGroup.setMaxLines(lines);
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

/**
 * Arp table entry.
 */
class Arp {
    String num;
    String ip;
    String mac;
    String _if;
    String expire;

    static Arp newInstance(JSONObject arpEntry, int line) {
        Arp arp = new Arp();
        try {
            arp.num = Integer.toString(line);
            arp.ip = arpEntry.getString("IP");
            arp.mac = arpEntry.getString("MAC");
            arp._if = arpEntry.getString("Interface");
            arp.expire = arpEntry.getString("Expire");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Arp newInstance exception: " + e.toString());
        }
        return arp;
    }
}

class ArpTableRecyclerAdapter extends RecyclerView.Adapter<ArpTableRecyclerAdapter.ArpViewHolder> {

    private final List<Arp> arpList;

    class ArpViewHolder extends RecyclerView.ViewHolder {
        final TextView onIf;
        final TextView ip;
        final TextView expire;
        public final TextView number;
        final TextView mac;
        public final TextView image;


        ArpViewHolder(View view) {
            super(view);
            onIf = (TextView) view.findViewById(R.id.onIf);
            ip = (TextView) view.findViewById(R.id.ip);
            expire = (TextView) view.findViewById(R.id.expire);
            number = (TextView) view.findViewById(R.id.number);
            mac = (TextView) view.findViewById(R.id.mac);
            image = (TextView) view.findViewById(R.id.image);
        }
    }

    ArpTableRecyclerAdapter(List<Arp> list) {
        this.arpList = list;
    }

    @Override
    public ArpViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.arp, parent, false);

        return new ArpViewHolder(itemView);
    }

    /**
     * Populate recycler view item.
     *
     * @param holder   Container view.
     * @param position Item position.
     */
    @Override
    public void onBindViewHolder(ArpViewHolder holder, int position) {

        Arp arp = arpList.get(position);

        holder.onIf.setText(String.format(holder.onIf.getResources().getString(R.string.on_if), arp._if));
        holder.ip.setText(arp.ip);
        holder.number.setText(arp.num);
        holder.mac.setText(arp.mac);
        holder.expire.setText(arp.expire);

        int image;
        String caption;

        if (arp.expire.equals("expired")) {
            image = R.drawable.block;
            caption = "E";
        } else if (arp.expire.equals("permanent")) {
            image = R.drawable.pass;
            caption = "P";
        } else {
            image = R.drawable.match;
            caption = "A";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return arpList.size();
    }
}

/**
 * Dhcp lease entry.
 */
class Lease {
    String num;
    String ip;
    public String start;
    public String end;
    String mac;
    String host;
    String status;

    static Lease newInstance(JSONObject leaseEntry, int line) {
        Lease lease = new Lease();
        try {
            lease.num = Integer.toString(line);
            lease.ip = leaseEntry.getString("IP");
            lease.start = leaseEntry.getString("Start");
            lease.end = leaseEntry.getString("End");
            lease.mac = leaseEntry.getString("MAC");
            lease.host = leaseEntry.getString("Host");
            lease.status = leaseEntry.getString("Status");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Lease newInstance exception: " + e.toString());
        }
        return lease;
    }
}

class LeaseRecyclerAdapter extends RecyclerView.Adapter<LeaseRecyclerAdapter.LeaseViewHolder> {

    private final List<Lease> leaseList;

    class LeaseViewHolder extends RecyclerView.ViewHolder {
        final TextView startEnd;
        final TextView ip;
        final TextView status;
        public final TextView number;
        final TextView host;


        LeaseViewHolder(View view) {
            super(view);
            startEnd = (TextView) view.findViewById(R.id.startEnd);
            ip = (TextView) view.findViewById(R.id.ip);
            status = (TextView) view.findViewById(R.id.status);
            number = (TextView) view.findViewById(R.id.number);
            host = (TextView) view.findViewById(R.id.host);
        }
    }

    LeaseRecyclerAdapter(List<Lease> list) {
        this.leaseList = list;
    }

    @Override
    public LeaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.lease, parent, false);

        return new LeaseViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LeaseViewHolder holder, int position) {

        Lease lease = leaseList.get(position);

        holder.startEnd.setText(lease.start + " - " + lease.end);
        holder.ip.setText(lease.ip + " - " + lease.mac);
        holder.number.setText(lease.num);
        holder.host.setText(lease.host);
        // TODO: Check why Json assigns "null" to empty string?
        holder.status.setText(lease.status.equals("null") ? "" : lease.status);

    }

    @Override
    public int getItemCount() {
        return leaseList.size();
    }
}

class InfoHostsCache {
    public final Bundle bundle = new Bundle();

    JSONArray mDhcpdJsonArray;
    JSONArray mNamedJsonArray;
    JSONArray mArpTableJsonArray;
    JSONArray mLeasesJsonArray;
}
