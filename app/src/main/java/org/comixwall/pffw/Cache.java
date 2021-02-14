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

import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.json.JSONObject;

public class Cache extends Fragment {

    public InfoSystemCache infoSystem;
    public InfoHostsCache infoHosts;
    public InfoIfsCache infoIfs;
    public InfoRulesCache infoRules;
    public InfoStatesCache infoStates;
    public InfoQueuesCache infoQueues;

    public StatsGeneralCache statsGeneral;
    public StatsCache statsDaily;
    public StatsCache statsHourly;
    public StatsCache statsLive;

    public GraphsIfsCache graphsIfs;
    public GraphsCache graphsTransfer;
    public GraphsStatesCache graphsStates;
    public GraphsCache graphsMbufs;

    public LogsCache logsArchive;
    public LogsCache logsLive;

    public DashboardCache dashboard;

    public JSONObject mJsonLogFileList = new JSONObject();

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }
}
