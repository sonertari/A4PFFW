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

import static org.comixwall.pffw.MainActivity.cache;
import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class InfoRules extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        RefreshTimer.OnTimeoutListener, RecyclerTouchListener.OnItemClickListener,
        ControllerTask.ControllerTaskListener {

    private InfoRulesCache mModuleCache;

    private RefreshTimer mTimer;
    private int mRefreshTimeout = 10;

    private SwipeRefreshLayout swipeRefresh;

    private JSONArray mRulesJsonArray;
    private final List<Rule> mRulesList = new ArrayList<>();
    private RuleRecyclerAdapter mRulesAdapter;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.info_rules, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this);

        RecyclerView rvRules = view.findViewById(R.id.recyclerViewRules);

        rvRules.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvRules.setItemAnimator(new DefaultItemAnimator());
        rvRules.addItemDecoration(new RecyclerDivider(getActivity(), LinearLayoutManager.VERTICAL));
        mRulesAdapter = new RuleRecyclerAdapter(mRulesList);
        rvRules.setAdapter(mRulesAdapter);
        rvRules.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), this));

        if (cache.infoRules == null) {
            cache.infoRules = new InfoRulesCache();
        }
        mModuleCache = cache.infoRules;

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        mModuleCache.mRulesJsonArray = mRulesJsonArray;

        // ATTENTION: It is very important to cancel the timer
        mTimer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;

        mRulesJsonArray = mModuleCache.mRulesJsonArray;

        if (mRulesJsonArray == null) {
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
            String output = controller.execute("pf", "GetPfRulesInfo");

            JSONArray jsonArray = new JSONArray(output);
            mRulesJsonArray = new JSONArray(jsonArray.get(0).toString());

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
        updateRuleList(mRulesJsonArray, mRulesList);
        mRulesAdapter.notifyDataSetChanged();
    }

    private void updateRuleList(JSONArray jsonArray, List<Rule> list) {
        try {
            list.clear();

            int i = 0;
            while (i < jsonArray.length()) {
                list.add(Rule.newInstance(jsonArray.getJSONObject(i++)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("updateRuleList exception= " + e.toString());
        }
    }

    @Override
    public void onRefresh() {
        getInfo();
    }

    @Override
    public void onItemClick(View view) {

        TextView tvRule = view.findViewById(R.id.rule);
        TextView tvEvalsStates = view.findViewById(R.id.evalsStates);

        int lines = 10;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (tvRule.getMaxLines() != 1) {
                lines = 1;
            }
        }

        tvRule.setMaxLines(lines);
        tvEvalsStates.setMaxLines(lines);
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

class Rule {
    String num;
    String rule;
    String evaluations;
    String packets;
    String bytes;
    String states;
    String inserted;
    String stateCreations;

    static Rule newInstance(JSONObject ruleEntry) {
        Rule rule = new Rule();
        try {
            rule.num = ruleEntry.getString("number");
            rule.rule = ruleEntry.getString("rule");
            rule.evaluations = ruleEntry.getString("evaluations");
            rule.packets = ruleEntry.getString("packets");
            rule.bytes = ruleEntry.getString("bytes");
            rule.states = ruleEntry.getString("states");
            rule.inserted = ruleEntry.getString("inserted");
            rule.stateCreations = ruleEntry.getString("stateCreations");
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning("Rule newInstance exception= " + e.toString());
        }
        return rule;
    }
}

class RuleRecyclerAdapter extends RecyclerView.Adapter<RuleRecyclerAdapter.RuleViewHolder> {

    private final List<Rule> ruleList;

    class RuleViewHolder extends RecyclerView.ViewHolder {
        final TextView packetsBytes;
        final TextView rule;
        final TextView evalsStates;
        final TextView number;
        final TextView inserted;
        final TextView image;


        RuleViewHolder(View view) {
            super(view);
            packetsBytes = view.findViewById(R.id.packetsBytes);
            rule = view.findViewById(R.id.rule);
            evalsStates = view.findViewById(R.id.evalsStates);
            number = view.findViewById(R.id.number);
            inserted = view.findViewById(R.id.inserted);
            image = view.findViewById(R.id.image);
        }
    }

    RuleRecyclerAdapter(List<Rule> list) {
        this.ruleList = list;
    }

    @Override
    public RuleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rule, parent, false);

        return new RuleViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(RuleViewHolder holder, int position) {

        Rule rule = ruleList.get(position);

        holder.packetsBytes.setText(String.format(holder.packetsBytes.getResources().getString(R.string.packets_bytes), rule.packets, rule.bytes));
        holder.rule.setText(rule.rule);
        holder.number.setText(rule.num);
        holder.inserted.setText(rule.inserted);
        holder.evalsStates.setText(String.format(holder.evalsStates.getResources().getString(R.string.evals_states_statecreats), rule.evaluations, rule.states, rule.stateCreations));

        int image;
        String caption;

        if (rule.rule.matches("^block\\s+.*")) {
            image = R.drawable.block;
            caption = "B";
        } else if (rule.rule.matches("^pass\\s+.*")) {
            image = R.drawable.pass;
            caption = "P";
        } else {
            image = R.drawable.match;
            caption = "M";
        }

        holder.image.setBackgroundResource(image);
        holder.image.setText(caption);
    }

    @Override
    public int getItemCount() {
        return ruleList.size();
    }
}

class InfoRulesCache {
    JSONArray mRulesJsonArray;
}
