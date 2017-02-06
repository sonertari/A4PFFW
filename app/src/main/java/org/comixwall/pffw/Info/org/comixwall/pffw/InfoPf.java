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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class InfoPf extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, ControllerTask.ControllerTaskListener {

    private LogsCache mModuleCache;

    private View view;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private String mPfStatus;
    private String mPfInfo;
    private String mPfMem;
    private String mPfTimeout;

    private TextView tvPfStatus;
    private TextView tvPfInfo;
    private TextView tvPfMem;
    private TextView tvPfTimeout;

    private ImageView ivPfStatus;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.info_pf, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        tvPfStatus = (TextView) view.findViewById(R.id.pfStatus);
        tvPfInfo = (TextView) view.findViewById(R.id.pfInfo);
        tvPfMem = (TextView) view.findViewById(R.id.pfMem);
        tvPfTimeout = (TextView) view.findViewById(R.id.pfTimeout);

        tvPfInfo.setOnClickListener(mLabelClickedHandler);
        tvPfTimeout.setOnClickListener(mLabelClickedHandler);

        ivPfStatus = (ImageView) view.findViewById(R.id.imageViewPfStatus);

        // TODO: How to resize the pf info cardview to pf memory size initially? Should be one time only.
        //tvPfInfo.performClick();

        if (cache.logsLive == null) {
            cache.logsLive = new LogsCache();
        }
        mModuleCache = cache.logsLive;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.bundle.putString("mPfStatus", mPfStatus);
        mModuleCache.bundle.putString("mPfInfo", mPfInfo);
        mModuleCache.bundle.putString("mPfMem", mPfMem);
        mModuleCache.bundle.putString("mPfTimeout", mPfTimeout);

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mPfStatus = mModuleCache.bundle.getString("mPfStatus");

        if (mPfStatus == null) {
            getInfo();
        } else {
            mPfInfo = mModuleCache.bundle.getString("mPfInfo");
            mPfMem = mModuleCache.bundle.getString("mPfMem");
            mPfTimeout = mModuleCache.bundle.getString("mPfTimeout");

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

    /**
     * Fetch pf status, stats, memory info, and timeout settings.
     * <p>
     * We also fetch the reload rate to update the page refresh timeout.
     * <p>
     * We save the exception message in mLastError, so that it can be displayed to the user
     * on post execution. Otherwise, this method runs on an async task, not on the UI thread,
     * so it cannot show messages to the user.
     *
     * @return True on success, false on failure.
     */
    @Override
    public boolean executeTask() {
        try {
            String output = controller.execute("pf", "IsRunning");

            mPfStatus = new JSONArray(output).get(2).toString();

            output = controller.execute("pf", "GetPfInfo");

            mPfInfo = new JSONArray(output).get(0).toString();

            output = controller.execute("pf", "GetPfMemInfo");

            mPfMem = new JSONArray(output).get(0).toString();

            output = controller.execute("pf", "GetPfTimeoutInfo");

            mPfTimeout = new JSONArray(output).get(0).toString();

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

        try {
            Utils.updateStatusViews(mPfStatus, ivPfStatus, tvPfStatus, getString(R.string.packet_filter));

            tvPfInfo.setText(mPfInfo);
            tvPfMem.setText(mPfMem);
            tvPfTimeout.setText(mPfTimeout);

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateInfo exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                View cv;
                int newHeight = view.findViewById(R.id.pfMemCardView).getHeight() * 2;

                if (id == R.id.pfInfo) {
                    cv = view.findViewById(R.id.pfInfoCardView);
                } else {
                    cv = view.findViewById(R.id.pfTimeoutCardView);
                }

                LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) cv.getLayoutParams();
                llParams.height = llParams.height == LinearLayout.LayoutParams.WRAP_CONTENT ?
                        newHeight : LinearLayout.LayoutParams.WRAP_CONTENT;

                cv.setLayoutParams(llParams);

            } catch (Exception e) {
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
                e.printStackTrace();
            }
        }
    };

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

