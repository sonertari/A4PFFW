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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;

public class InfoStates extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoStatesCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mStatesJsonArray;
    private final List<State> mStatesList = new ArrayList<>();
    private StateRecyclerAdapter mAdapter;

    private TextView tvStateSize;
    private EditText etStartLine, etLinesPerPage, etRegex;

    private int mButton;
    private boolean mButtonPressed = false;
    private int mLinesPerPage, mStateSize = 0, mStartLine, mHeadStart;
    private String mRegex = "";

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_states, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        tvStateSize = (TextView) view.findViewById(R.id.logSize);

        etStartLine = (EditText) view.findViewById(R.id.startLine);
        etLinesPerPage = (EditText) view.findViewById(R.id.editTextLinesPerPage);
        etRegex = (EditText) view.findViewById(R.id.editTextRegex);

        view.findViewById(R.id.first).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.previous).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.next).setOnClickListener(mLabelClickedHandler);
        view.findViewById(R.id.last).setOnClickListener(mLabelClickedHandler);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mAdapter = new StateRecyclerAdapter(mStatesList);
        recyclerView.setAdapter(mAdapter);
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.infoStates == null) {
            cache.infoStates = new InfoStatesCache();
        }
        mModuleCache = cache.infoStates;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mStatesJsonArray = mStatesJsonArray;

        mModuleCache.bundle.putInt("mLinesPerPage", mLinesPerPage);
        mModuleCache.bundle.putInt("mStartLine", mStartLine);
        mModuleCache.bundle.putInt("mStateSize", mStateSize);
        mModuleCache.bundle.putString("mRegex", mRegex);

        /// @attention It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
        mStatesJsonArray = mModuleCache.mStatesJsonArray;

        if (mStatesJsonArray == null) {
            getStates();
        } else {

            mLinesPerPage = mModuleCache.bundle.getInt("mLinesPerPage");
            mStartLine = mModuleCache.bundle.getInt("mStartLine");
            mStateSize = mModuleCache.bundle.getInt("mStateSize");
            mRegex = mModuleCache.bundle.getString("mRegex");

            updateSelections();
            updateStates();
        }

        // Schedule the timer here, not in onCreateView(), because mRefreshTimeout may be updated in getStates()
        mTimer = new RefreshTimer((MainActivity) getActivity(), this);
        mTimer.start(mRefreshTimeout);
    }

    @Override
    public void onTimeout() {
        getStates();
    }

    @Override
    public void executePreTask() {
        getSelections();
    }

    @Override
    public void preExecute() {
        swipeRefresh.setRefreshing(true);
    }

    @Override
    public boolean executeTask() {
        try {
            String output = controller.execute("GetStateCount", mRegex);

            mStateSize = new JSONArray(output).getInt(0);

            computeNavigationVars();

            String states = controller.execute("GetStateList", mHeadStart, mLinesPerPage, mRegex);
            JSONArray jsonArray = new JSONArray(states);
            mStatesJsonArray = new JSONArray(jsonArray.get(0).toString());

            output = controller.execute("GetReloadRate");

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
        updateSelections();

        if (result) {
            updateStates();
        } else {
            Toast.makeText(getContext(), "Error: " + mLastError, Toast.LENGTH_SHORT).show();
        }

        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void executeOnCancelled() {
        swipeRefresh.setRefreshing(false);
    }

    private void getStates() {
        ControllerTask.run(this, this);
    }

    private void updateStates() {
        try {
            mStatesList.clear();

            int i = 0;
            while (i < mStatesJsonArray.length()) {
                JSONArray stateLine = mStatesJsonArray.getJSONArray(i);
                State state = State.newInstance(stateLine, mStartLine + i + 1);
                mStatesList.add(state);
                i++;
            }

            mAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateStates exception: " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getStates();
    }

    private void updateSelections() {
        tvStateSize.setText(String.format(Locale.getDefault(), "/ %1$d", mStateSize));

        etStartLine.setText(String.format(Locale.getDefault(), "%1$d", mStartLine + 1));
        etLinesPerPage.setText(String.format(Locale.getDefault(), "%1$d", mLinesPerPage));
        etRegex.setText(mRegex);
    }

    private void getSelections() {
        try {
            mStartLine = Integer.parseInt(etStartLine.getText().toString()) - 1;
        } catch (Exception e) {
            mStartLine = 0;
        }
        try {
            /// @attention Never allow too large numbers here.
            /// @bug tail(1) on OpenBSD 5.9 amd64 gets stuck with: echo soner | /usr/bin/tail -99999999
            mLinesPerPage = Math.min(999, Integer.parseInt(etLinesPerPage.getText().toString()));
        } catch (Exception e) {
            mLinesPerPage = 25;
        }
        mRegex = etRegex.getText().toString();
    }

    private void computeNavigationVars() {

        if (mButtonPressed) {
            if (mButton == R.id.first) {
                mStartLine = 0;
            } else if (mButton == R.id.previous) {
                mStartLine -= mLinesPerPage;
            } else if (mButton == R.id.next) {
                mStartLine += mLinesPerPage;
            } else if (mButton == R.id.last) {
                mStartLine = mStateSize;
            }
            mButtonPressed = false;
        }

        mHeadStart = mStartLine + mLinesPerPage;
        if (mHeadStart > mStateSize) {
            mHeadStart = mStateSize;
            mStartLine = mHeadStart - mLinesPerPage;
        }
        if (mStartLine < 0) {
            mStartLine = 0;
            mHeadStart = mLinesPerPage;
        }
    }

    private final View.OnClickListener mLabelClickedHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int id = v.getId();

                mButtonPressed = true;
                mButton = id;
                getStates();

            } catch (Exception e) {
                logger.warning("mLabelClickedHandler onClick exception: " + e.toString());
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onItemClick(View view) {
        TextView tvSrcDst = (TextView) view.findViewById(R.id.srcDst);
        TextView tvOthers = (TextView) view.findViewById(R.id.others);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvSrcDst.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvSrcDst.setMaxLines(lines);
        tvOthers.setMaxLines(lines);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            getStates();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

class State {
    String num;
    String proto;
    String dir;
    String src;
    String dst;
    String state;
    String age;
    String expr;
    String pkts;
    String bytes;

    static State newInstance(JSONArray stateLine, int line) {
        State state = new State();
        try {
            state.num = Integer.toString(line);
            state.proto = stateLine.getString(0);
            state.dir = stateLine.getString(1);
            state.src = stateLine.getString(2);
            state.dst = stateLine.getString(3);
            state.state = stateLine.getString(4);
            state.age = stateLine.getString(5);
            state.expr = stateLine.getString(6);
            state.pkts = stateLine.getString(7);
            state.bytes = stateLine.getString(8);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("State newInstance exception: " + e.toString());
        }
        return state;
    }
}

class StateRecyclerAdapter extends RecyclerView.Adapter<StateRecyclerAdapter.StateViewHolder> {

    private final List<State> statesList;

    class StateViewHolder extends RecyclerView.ViewHolder {
        public final TextView number;
        final TextView state;
        final TextView srcDst;
        final TextView ageExpr;
        final TextView others;
        public final TextView image;


        StateViewHolder(View view) {
            super(view);
            number = (TextView) view.findViewById(R.id.number);
            state = (TextView) view.findViewById(R.id.state);
            srcDst = (TextView) view.findViewById(R.id.srcDst);
            ageExpr = (TextView) view.findViewById(R.id.ageExpr);
            others = (TextView) view.findViewById(R.id.others);
            image = (TextView) view.findViewById(R.id.image);
        }
    }


    StateRecyclerAdapter(List<State> list) {
        this.statesList = list;
    }

    @Override
    public StateViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.state, parent, false);

        return new StateViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(StateViewHolder holder, int position) {

        State state = statesList.get(position);

        holder.number.setText(state.num);
        holder.state.setText(state.state);
        holder.srcDst.setText(state.src + " -> " + state.dst);
        holder.ageExpr.setText(state.expr);
        holder.others.setText(state.proto + ", " + state.dir + ", pkts: " + state.pkts + ", bytes: " + state.bytes + ", age: " + state.age);

        int image;
        String caption;

        if (state.state.contains("SYN")) {
            image = R.drawable.block;
            caption = "S";
        } else if (state.state.contains("ESTABLISHED")) {
            image = R.drawable.pass;
            caption = "E";
        } else {
            image = R.drawable.match;
            caption = "F";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return statesList.size();
    }
}

class InfoStatesCache {
    public final Bundle bundle = new Bundle();

    JSONArray mStatesJsonArray;
}
