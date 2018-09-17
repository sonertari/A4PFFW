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
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;

public class NotificationDetails extends Fragment implements RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private static final List<NotificationDetail> mNotificationDetails = new ArrayList<>();

    public static void setNotificationDetails(List<NotificationDetail> details) {
        mNotificationDetails.clear();
        mNotificationDetails.addAll(details);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.notification_details, container, false);

        Bundle args = getArguments();
        ((TextView) view.findViewById(R.id.title)).setText(args.getString("title"));
        ((TextView) view.findViewById(R.id.body)).setText(args.getString("body"));
        ((TextView) view.findViewById(R.id.datetime)).setText(args.getString("datetime"));

        RecyclerView rvNotifications = view.findViewById(R.id.recyclerViewNotificationDetails);

        rvNotifications.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvNotifications.setItemAnimator(new DefaultItemAnimator());
        rvNotifications.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        rvNotifications.setAdapter(new NotificationDetailRecyclerAdapter(mNotificationDetails));
        rvNotifications.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        fragment = this;
    }

    @Override
    public void onItemClick(View view) {

        TextView tvLog = view.findViewById(R.id.log);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvLog.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvLog.setMaxLines(lines);
    }

    // ATTENTION: ControllerTask.ControllerTaskListener interface implementation is for deleting token
    // During logout we send delToken command to the firewall using the refresh menu option
    // So all fragments should implement it
    @Override
    public void executePreTask() {
    }

    @Override
    public void preExecute() {
    }

    @Override
    public boolean executeTask() {
        try {
            // Dummy call
            controller.execute("pf", "IsRunning");
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void postExecute(boolean result) {
    }

    @Override
    public void executeOnCancelled() {
    }

    private void getInfo() {
        ControllerTask.run(this, this);
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

class NotificationDetail {
    String module;
    String process;
    String log;
    String prio;
    String datetime;

    static NotificationDetail newInstance(String module, JSONObject notificationEntry) {
        NotificationDetail notification = new NotificationDetail();
        try {
            notification.module = module;
            notification.process = notificationEntry.getString("Process");
            notification.log = notificationEntry.getString("Log");
            notification.prio = notificationEntry.getString("Prio");
            notification.datetime = notificationEntry.getString("Date") + " " + notificationEntry.getString("Time");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Notification newInstance exception= " + e.toString());
        }
        return notification;
    }
}

class NotificationDetailRecyclerAdapter extends RecyclerView.Adapter<NotificationDetailRecyclerAdapter.NotificationDetailViewHolder> {

    private final List<NotificationDetail> notificationList;

    class NotificationDetailViewHolder extends RecyclerView.ViewHolder {
        final TextView process;
        final TextView log;
        final TextView prio;
        final TextView datetime;
        final TextView image;


        NotificationDetailViewHolder(View view) {
            super(view);
            process = view.findViewById(R.id.process);
            log = view.findViewById(R.id.log);
            prio = view.findViewById(R.id.prio);
            datetime = view.findViewById(R.id.datetime);
            image = view.findViewById(R.id.image);
        }
    }

    NotificationDetailRecyclerAdapter(List<NotificationDetail> list) {
        this.notificationList = list;
    }

    @Override
    public NotificationDetailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.notification_detail, parent, false);

        return new NotificationDetailViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(NotificationDetailViewHolder holder, int position) {
        NotificationDetail notification = notificationList.get(position);

        holder.process.setText(notification.process);
        holder.log.setText(notification.log);
        holder.prio.setText(notification.module + " " + notification.prio);
        holder.datetime.setText(notification.datetime);

        int image;
        String caption;

        if (notification.prio.contains("CRITICAL") || notification.prio.contains("ALERT") || notification.prio.contains("EMERGENCY")) {
            image = R.drawable.block;
            caption = "C";
        } else if (notification.prio.contains("ERROR")) {
            image = R.drawable.match;
            caption = "E";
        } else {
            image = R.drawable.pass;
            caption = "W";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }
}
