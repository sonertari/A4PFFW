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

import android.graphics.Color;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class Dashboard extends Fragment implements  SwipeRefreshLayout.OnRefreshListener,
        RecyclerTouchListener.OnItemClickListener, RefreshTimer.OnTimeoutListener,
        ControllerTask.ControllerTaskListener {

    private DashboardCache mModuleCache;

    private JSONObject mServiceStatus;
    private static final List<DashboardEntry> mDashboardList = new ArrayList<>();

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;
    private DashboardEntryRecyclerAdapter mAdapter;

    private String mLastError;

    private TextView tvCritical;
    private TextView tvError;
    private TextView tvWarning;

    private HashMap<String, String> mModuleNames;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.dashboard, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        tvCritical = view.findViewById(R.id.critical);
        tvError = view.findViewById(R.id.error);
        tvWarning = view.findViewById(R.id.warning);

        RecyclerView rvDashboard = view.findViewById(R.id.recyclerViewDashboard);
        rvDashboard.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvDashboard.setItemAnimator(new DefaultItemAnimator());
        rvDashboard.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mAdapter = new DashboardEntryRecyclerAdapter(mDashboardList);
        rvDashboard.setAdapter(mAdapter);
        rvDashboard.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        mModuleNames = new HashMap<String, String>() {{
            put("system", getString(R.string.System));
            put("pf", getString(R.string.PacketFilter));
            put("dhcpd", getString(R.string.DHCPServer));
            put("named", getString(R.string.DNSServer));
            put("openssh", getString(R.string.OpenSSH));
            put("ftp-proxy", getString(R.string.FTPProxy));
            put("httpd", getString(R.string.WebServer));
            put("symon", getString(R.string.Symon));
            put("symux", getString(R.string.Symux));
            put("sslproxy", getString(R.string.SSLProxy));
            put("e2guardian", getString(R.string.WebFilter));
            put("snort", getString(R.string.IDS));
            put("snortinline", getString(R.string.InlineIPS));
            put("snortips", getString(R.string.PassiveIPS));
            put("spamassassin", getString(R.string.SPAMFilter));
            put("clamd", getString(R.string.VirusFilter));
            put("freshclam", getString(R.string.VirusDBUpdate));
            put("p3scan", getString(R.string.POP3Proxy));
            put("smtp-gated", getString(R.string.SMTPProxy));
            put("imspector", getString(R.string.IMProxy));
            put("openvpn", getString(R.string.OpenVPN));
            put("dante", getString(R.string.SOCKSProxy));
            put("spamd", getString(R.string.SPAMDeferral));
            put("pmacct", getString(R.string.Pmacct));
            put("collectd", getString(R.string.Collectd));
        }};

        if (cache.dashboard == null) {
            cache.dashboard = new DashboardCache();
        }
        mModuleCache = cache.dashboard;
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mServiceStatus = mServiceStatus;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mServiceStatus = mModuleCache.mServiceStatus;

        if (mServiceStatus == null) {
            getStatus();
        } else {
            updateStatus();
        }

        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getStatus()
        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        mTimer.start(mRefreshTimeout);
    }

    @Override
    public void onTimeout() {
        getStatus();
    }

    @Override
    public void onItemClick(View view) {

        TextView tvModule = view.findViewById(R.id.module);
        TextView tvErrors = view.findViewById(R.id.errors);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvModule.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvModule.setMaxLines(lines);
        tvErrors.setMaxLines(lines);
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
        try {
            String output = controller.execute("system", "GetServiceStatus");

            JSONArray jsonArray = new JSONArray(output);
            mServiceStatus = new JSONObject(jsonArray.get(0).toString()).getJSONObject("status");

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
        if (result) {
            updateStatus();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    private void getStatus() {
        ControllerTask.run(this, this);
    }

    private void updateStatus() {
        try {
            mDashboardList.clear();

            int critical = 0;
            int error = 0;
            int warning = 0;

            Iterator<String> modules = mServiceStatus.keys();
            while (modules.hasNext()) {
                String module = modules.next();
                JSONObject jsonModuleStatus = mServiceStatus.getJSONObject(module);
                mDashboardList.add(DashboardEntry.newInstance(mModuleNames.get(module), jsonModuleStatus));

                critical += jsonModuleStatus.getInt("Critical");
                error += jsonModuleStatus.getInt("Error");
                warning += jsonModuleStatus.getInt("Warning");
            }

            tvCritical.setText("" + critical);
            tvError.setText("" + error);
            tvWarning.setText("" + warning);

            mAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateStatus exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getStatus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getStatus();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

class DashboardEntry {
    String status;
    String errorStatus;
    String module;
    String errors;

    static DashboardEntry newInstance(String module, JSONObject jsonModuleStatus) {
        DashboardEntry dashboardEntry = new DashboardEntry();
        try {
            dashboardEntry.module = module;
            dashboardEntry.status = jsonModuleStatus.getString("Status");
            dashboardEntry.errorStatus = jsonModuleStatus.getString("ErrorStatus");

            int critical = jsonModuleStatus.getInt("Critical");
            int error = jsonModuleStatus.getInt("Error");
            int warning = jsonModuleStatus.getInt("Warning");

            String errors = "";
            if (critical > 0) {
                errors += "Critical: " + critical;
            }
            if (error > 0) {
                if (critical > 0) {
                    errors += ", ";
                }
                errors += "Error: " + error;
            }
            if (warning > 0) {
                if (critical > 0 || error > 0) {
                    errors += ", ";
                }
                errors += "Warning: " + warning;
            }
            dashboardEntry.errors = errors;

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("DashboardEntry newInstance exception= " + e.toString());
        }
        return dashboardEntry;
    }
}

class DashboardEntryRecyclerAdapter extends RecyclerView.Adapter<DashboardEntryRecyclerAdapter.DashboardEntryViewHolder> {

    private final List<DashboardEntry> dashboardEntryList;

    class DashboardEntryViewHolder extends RecyclerView.ViewHolder {
        final TextView status;
        final TextView errorStatus;
        final TextView module;
        final TextView errors;

        DashboardEntryViewHolder(View view) {
            super(view);
            status = view.findViewById(R.id.status);
            errorStatus = view.findViewById(R.id.errorStatus);
            module = view.findViewById(R.id.module);
            errors = view.findViewById(R.id.errors);
        }
    }

    DashboardEntryRecyclerAdapter(List<DashboardEntry> list) {
        this.dashboardEntryList = list;
    }

    @Override
    public DashboardEntryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dashboard_module_status, parent, false);

        return new DashboardEntryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(DashboardEntryViewHolder holder, int position) {
        DashboardEntry dashboardEntry = dashboardEntryList.get(position);

        // Use position as id, so we know the dashboardEntry that the user clicks on
        holder.itemView.setId(position);

        holder.module.setText(dashboardEntry.module);
        holder.errors.setText(dashboardEntry.errors);

        int image;

        if (dashboardEntry.status.contains("R")) {
            image = R.mipmap.running;
            holder.status.setTextColor(Color.WHITE);
        } else {
            image = R.drawable.stopped;
        }

        holder.status.setBackgroundResource(image);
        holder.status.setText(dashboardEntry.status);

        if (dashboardEntry.errorStatus.contains("C")) {
            image = R.drawable.critical;
        } else if (dashboardEntry.errorStatus.contains("E")) {
            image = R.drawable.error;
        } else if (dashboardEntry.errorStatus.contains("W")) {
            image = R.mipmap.warning;
        } else {
            image = R.drawable.noerror;
        }

        holder.errorStatus.setBackgroundResource(image);
        holder.errorStatus.setText(dashboardEntry.errorStatus);
    }

    @Override
    public int getItemCount() {
        return dashboardEntryList.size();
    }
}

class DashboardCache {
    JSONObject mServiceStatus;
}
