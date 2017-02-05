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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.getSslContext;

public abstract class GraphsBase extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, ControllerTask.ControllerTaskListener {

    GraphsCache mModuleCache;

    View view;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONObject mGraphsJsonObject;

    String mLayout = "ifs";

    private int mGraphWidth;
    private int mGraphHeight;

    private SSLContext sslContext = null;

    // Create host name verifier for PFFW host
    private HostnameVerifier hostnameVerifier = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return hostname.equals(controller.getHost()) || hostname.equals(controller.getHostname());
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        init(inflater, container);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        sslContext = getSslContext(this);

        return view;
    }

    protected abstract void init(LayoutInflater inflater, ViewGroup container);

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mGraphsJsonObject = mGraphsJsonObject;

        saveImages();

        /// @attention It is very important to cancel the timer
        mTimer.cancel();
    }

    protected abstract void saveImages();

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;

        mGraphsJsonObject = mModuleCache.mGraphsJsonObject;

        if (mGraphsJsonObject == null) {
            getGraphs();
        } else {
            restoreImages();
            updateImages();
        }

        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in loadURL()
        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        mTimer.start(mRefreshTimeout);
    }

    protected abstract void restoreImages();

    @Override
    public void executePreTask() {
        /// @attention Measured width of swipeRefresh may be 0 on rotation, which gives negative w otherwise
        int w = swipeRefresh.getMeasuredWidth() - 180;
        mGraphWidth = w > 0 ? w : 900;
        mGraphHeight = Math.round(mGraphWidth / 3f);
        //final int height = Math.round(width / 1.78f);
    }

    @Override
    public void preExecute() {
        swipeRefresh.setRefreshing(true);
    }

    @Override
    public boolean executeTask() {
        Boolean retval = true;
        try {
            String output = controller.execute("symon", "RenderLayout", mLayout, mGraphWidth, mGraphHeight);

            JSONArray jsonArray = new JSONArray(output);
            mGraphsJsonObject = new JSONObject(jsonArray.get(0).toString());

            Iterator<String> it = mGraphsJsonObject.keys();
            while (it.hasNext()) {
                String title = it.next();
                String hash = mGraphsJsonObject.getString(title);

                try {
                    InputStream stream = null;

                    try {
                        // Using https here gives: CertPathValidatorException: Trust anchor for certification path not found.
                        // So we should trust the PFFW server crt and hostname
                        URL secureUrl = new URL("https://" + controller.getHost() + "/symon/graph.php?" + hash);

                        HttpsURLConnection secureUrlConn = (HttpsURLConnection) secureUrl.openConnection();

                        // Tell the URLConnection to use a SocketFactory from our SSLContext
                        secureUrlConn.setSSLSocketFactory(sslContext.getSocketFactory());

                        // Install the PFFW host verifier
                        secureUrlConn.setHostnameVerifier(hostnameVerifier);

                        logger.finest("Using secure http: " + secureUrl.toString());

                        /// @attention Setting a timeout value enables SocketTimeoutException
                        secureUrlConn.setReadTimeout(5000);

                        stream = secureUrlConn.getInputStream();

                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.warning("Secure URL connection exception: " + e.toString());
                    }

                    // Try plain if secure fails
                    if (stream == null) {
                        URL plainUrl = new URL("http://" + controller.getHost() + "/symon/graph.php?" + hash);
                        HttpURLConnection plainUrlConn = (HttpURLConnection) plainUrl.openConnection();

                        logger.finest("Using plain http: " + plainUrlConn.toString());

                        /// @attention Setting a timeout value enables SocketTimeoutException
                        plainUrlConn.setReadTimeout(5000);

                        stream = plainUrlConn.getInputStream();
                    }

                    Bitmap bmp = BitmapFactory.decodeStream(stream);
                    setBitmap(title, bmp);

                } catch (Exception e) {
                    // We are especially interested in SocketTimeoutException, but catch all
                    e.printStackTrace();
                    logger.info("doInBackground exception: " + e.toString());
                }
            }

            output = controller.execute("pf", "GetReloadRate");

            int timeout = Integer.parseInt(new JSONArray(output).get(0).toString());
            mRefreshTimeout = timeout < 10 ? 10 : timeout;

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("doInBackground exception: " + e.toString());
            retval = false;
        }
        return retval;
    }

    protected abstract void setBitmap(String title, Bitmap bmp);

    @Override
    public void postExecute(boolean result) {
        if (result) {
            updateImages();
        }

        swipeRefresh.setRefreshing(false);
    }

    protected abstract void updateImages();


    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    private void getGraphs() {
        ControllerTask.run(this, this);
    }

    final View.OnClickListener onViewClick = (new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logger.finest("Show Graph Dialog");

            android.app.FragmentTransaction ft = getActivity().getFragmentManager().beginTransaction();

            GraphDialog dialog = new GraphDialog();
            dialog.setBitmap(getBitmap(view));
            dialog.show(ft, "Graph Dialog");
        }
    });

    protected abstract Bitmap getBitmap(View view);

    @Override
    public void onTimeout() {
        getGraphs();
    }

    @Override
    public void onRefresh() {
        /// @attention Do not check if a task is started upon a swipe gesture: No need to implement a return value for run()
        /// Because we want the progress bar to be on if a controller task is running,
        /// whether as a result of the last swipe gesture or not.
        getGraphs();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getGraphs();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

class GraphsCache {
    JSONObject mGraphsJsonObject;
    Bitmap bmp;
}
