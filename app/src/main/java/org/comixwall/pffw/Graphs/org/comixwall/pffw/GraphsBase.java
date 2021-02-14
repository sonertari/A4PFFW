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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;

/**
 * Base class for all graphs fragments.
 */
public abstract class GraphsBase extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, ControllerTask.ControllerTaskListener {

    GraphsCache mModuleCache;

    View view;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    /**
     * Return value from the controller command.
     * This should contain KVPs from titles to graph hash names.
     */
    private JSONObject mGraphsJsonObject;

    /**
     * This is the name of the symon layout file.
     * Graphs are defined in such symon layout files.
     * Hence, this var is passed to the controller command as an argument.
     */
    String mLayout = "ifs";

    /**
     * Dimensions passed to the controller command for graph generation.
     */
    private int mGraphWidth;
    private int mGraphHeight;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        init(inflater, container);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        return view;
    }

    /**
     * Initialize fragment layout and view vars.
     * Graphs fragments do their initialization here. In this method the fragment should:
     * <ul>
     * <li>Inflate its layout
     * <li>Assign to its view variables
     * <li>Create its cache
     * </ul>
     * This method is called the first thing as the fragment view is created.
     *
     * @param inflater See {@link #onCreateView}
     * @param container See {@link #onCreateView}
     */
    protected abstract void init(LayoutInflater inflater, ViewGroup container);

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mGraphsJsonObject = mGraphsJsonObject;

        saveImages();

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    protected abstract void saveImages();

    /**
     * Resume fragment. If the mGraphsJsonObject var is null, we refresh the graphs.
     * Otherwise, we use the graphs available in the fragment cache.
     * <p>
     * We also make sure the static fragment var points to the current visible fragment.
     * <p>
     * All graphs pages refresh periodically, so this is where we start the refresh timer too.
     */
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

    /**
     * Compute graph dimensions.
     * Graph width and height is determined based on the current size of the screen.
     * This size depends on the device and current rotation.
     * <p>
     * The width and height computed here are passed to the Controller to be used while creating the image.
     */
    @Override
    public void executePreTask() {
        // ATTENTION: Measured width of swipeRefresh may be 0 on rotation, which gives negative w otherwise
        int w = swipeRefresh.getMeasuredWidth() - 180;
        mGraphWidth = w > 0 ? w : 900;

        // Limix min/max width
        mGraphWidth = mGraphWidth > 2048 ? 2048 : mGraphWidth;
        mGraphWidth = mGraphWidth < 32 ? 32 : mGraphWidth;

        mGraphHeight = Math.round(mGraphWidth / 3f);

        // Limix min/max height
        mGraphHeight = mGraphHeight > 2048 ? 2048 : mGraphHeight;
        mGraphHeight = mGraphHeight < 32 ? 32 : mGraphHeight;
    }

    @Override
    public void preExecute() {
        swipeRefresh.setRefreshing(true);
    }

    /**
     * Run the controller task.
     * We fetch the graphs using secure http, or fall back to plain http if secure connection fails.
     * <p>
     * Note that the PFFW uses a self-signed server certificate. So the code should trust that certificate
     * and not reject the hostname.
     *
     * @return True on success, false on failure.
     */
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
                String file = mGraphsJsonObject.getString(title);

                try {
                    InputStream stream = null;

                    try {
                        String outputGraph = controller.execute("symon", "GetGraph", file);
                        String base64Graph = new JSONArray(outputGraph).get(0).toString();
                        stream = new ByteArrayInputStream(Base64.decode(base64Graph, Base64.DEFAULT));

                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.warning("SSH graph connection exception: " + e.toString());
                    }

                    Bitmap bmp = BitmapFactory.decodeStream(stream);
                    setBitmap(title, bmp);

                } catch (Exception e) {
                    // We are especially interested in SocketTimeoutException, but catch all
                    e.printStackTrace();
                    logger.info("GraphsBase doInBackground exception: " + e.toString());
                    // We should break out of while loop on exception, because all conn attempts have failed
                    break;
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

    /**
     * Update the bmp variable with the downloaded bitmap.
     *
     * @param title The graph title.
     * @param bmp The graph.
     */
    protected abstract void setBitmap(String title, Bitmap bmp);

    /**
     * Update the graphs with the downloaded bitmaps.
     * This is where the bitmaps are really loaded to bitmap views, thus are displayed on the fragment layout.
     *
     * @param result Whether the graph download task was successful or not.
     */
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

    /**
     * Displays the graph in a dialog for pinch-zooming.
     */
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

    /**
     * Return the bitmap associated with the view.
     * Used by the graph dialog to load the bitmap the user has clicked on.
     *
     * @param view The view the user has clicked on.
     * @return The bitmap to display.
     */
    protected abstract Bitmap getBitmap(View view);

    /**
     * Refresh graphs periodically.
     */
    @Override
    public void onTimeout() {
        getGraphs();
    }

    /**
     * Refresh graphs upon swipe gesture.
     * <p>
     * ATTENTION: Do not check if a task is started upon a swipe gesture: No need to implement a return value for run().
     * Because we want the progress bar to be on if a controller task is running,
     * whether as a result of the last swipe gesture or not.
     */
    @Override
    public void onRefresh() {
        getGraphs();
    }

    /**
     * Handle app bar menu clicks.
     * This method is called by the handler of the activity, hence all fragments should implement it.
     * <p>
     * We currently have only refresh and logout options. Logout is handled by the activity.
     *
     * @param item The menu item clicked on.
     * @return See {@link Fragment#onOptionsItemSelected(MenuItem)}.
     */
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

/**
 * This is the cache class of the graphs pages.
 * Graphs fragments may have a different cache type with different vars based on their needs.
 */
class GraphsCache {
    JSONObject mGraphsJsonObject;
    Bitmap bmp;
}
