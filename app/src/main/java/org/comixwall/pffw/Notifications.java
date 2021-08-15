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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;

public class Notifications extends Fragment implements RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private static final List<Notification> mNotificationsList = new ArrayList<>();

    private RecyclerView rvNotifications;

    private static final ArrayList<String> priorities = new ArrayList<String>() {{
        add("Critical");
        add("Error");
        add("Warning");
    }};

    /**
     * Comparator used to sort notifications in reverse order.
     */
    static Comparator comparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((Notification) o2).datetime.compareTo(((Notification) o1).datetime);
        }
    };

    public static void addNotification(Notification element) {

        for (Notification n : mNotificationsList) {
            // ATTENTION: This comparison is a workaround for the case the user clicks the Overview button
            // If the activity was created with a notification intent while the app was in the background,
            // closing the app and then pressing the Overview button recreates the activity with the same intent,
            // hence we reach here and add the same notification one more time.
            // Timestamp is a unique notification id to prevent such mistakes
            // TODO: Find a way to fix this Overview button issue
            if (n.title.equals(element.title) && n.body.equals(element.body) && n.data.equals(element.data) && n.datetime.equals(element.datetime)) {
                logger.finest("addNotification will not add the same notification: " + element.datetime);
                return;
            }
        }

        // Recent notifications first, hence use id 0
        mNotificationsList.add(0, element);
        // Reverse sort in case the timestamp is older
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mNotificationsList.sort(comparator);
        }

        // Limit the number of notifications
        int count = mNotificationsList.size();
        if (count > 100) {
            logger.finest("addNotification reached max notification count, removing oldest notification, id= " + count);
            mNotificationsList.remove(count - 1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.notifications, container, false);

        rvNotifications = view.findViewById(R.id.recyclerViewNotifications);

        rvNotifications.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvNotifications.setItemAnimator(new DefaultItemAnimator());
        rvNotifications.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        rvNotifications.setAdapter(new NotificationRecyclerAdapter(mNotificationsList));
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

        TextView tvTitle = view.findViewById(R.id.title);
        TextView tvBody = view.findViewById(R.id.body);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvTitle.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvTitle.setMaxLines(lines);
        tvBody.setMaxLines(lines);

        List<NotificationDetail> details = new ArrayList<>();

        // Position == view id
        Notification notification = mNotificationsList.get(view.getId());
        try {
            JSONObject jsonArray = new JSONObject(notification.data);

            for (String p : priorities) {
                Iterator<String> modules = jsonArray.keys();
                while (modules.hasNext()) {
                    String module = modules.next();
                    Iterator<String> prios = jsonArray.getJSONObject(module).keys();
                    while (prios.hasNext()) {
                        String prio = prios.next();
                        if (prio.contains(p)) {
                            JSONArray logs = jsonArray.getJSONObject(module).getJSONArray(prio);
                            // There is only one sample log record, at index 0
                            details.add(NotificationDetail.newInstance(module, logs.getJSONObject(0)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("onItemClick exception= " + e.toString());
        }

        NotificationDetails.setNotificationDetails(details);

        // TODO: Code reuse, unite with MainActivity onNavigationItemSelected()
        fragment = new NotificationDetails();

        Bundle args = new Bundle();
        args.putString("title", notification.title);
        args.putString("body", notification.body);
        args.putString("datetime", notification.datetime);

        fragment.setArguments(args);

        FragmentManager fm = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        String fragmentName = fragment.getClass().getSimpleName();
        transaction.addToBackStack(fragmentName);

        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();

        ((MainActivity)getActivity()).createOptionsMenu();
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
        } else if (id == R.id.menuDelete) {
            deleteAllNotifications();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void deleteAllNotifications() {
        mNotificationsList.clear();
        NotificationRecyclerAdapter rvAdapter = (NotificationRecyclerAdapter)rvNotifications.getAdapter();
        rvAdapter.notificationList.clear();
        rvAdapter.notifyDataSetChanged();
    }
}

class Notification {
    String title;
    String body;
    String data;
    String datetime;

    static Notification newInstance(JSONObject notificationEntry) {
        Notification notification = new Notification();
        try {
            JSONObject data = new JSONObject(notificationEntry.getString("data"));
            notification.title = data.getString("title");
            notification.body = data.getString("body");
            notification.data = data.getString("data");
            notification.datetime = data.getString("datetime");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Notification newInstance exception= " + e.toString());
        }
        return notification;
    }
}

class NotificationRecyclerAdapter extends RecyclerView.Adapter<NotificationRecyclerAdapter.NotificationViewHolder> {

    public final List<Notification> notificationList = new ArrayList<>();

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView body;
        final TextView datetime;
        final TextView image;

        NotificationViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            body = view.findViewById(R.id.body);
            datetime = view.findViewById(R.id.datetime);
            image = view.findViewById(R.id.image);
        }
    }

    NotificationRecyclerAdapter(List<Notification> list) {
        this.notificationList.clear();
        this.notificationList.addAll(list);
    }

    @Override
    public NotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.notification, parent, false);

        return new NotificationViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(NotificationViewHolder holder, int position) {
        Notification notification = notificationList.get(position);

        // Use position as id, so we know the notification that the user clicks on
        holder.itemView.setId(position);

        holder.title.setText(notification.title);
        holder.body.setText(notification.body);
        holder.datetime.setText(notification.datetime);

        int image;
        String caption;

        if (notification.title.contains("critical errors")) {
            image = R.drawable.block;
            caption = "C";
        } else if (notification.title.contains("errors")) {
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
