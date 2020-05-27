/*
 * Copyright (C) 2017-2020 Soner Tari
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

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import static org.comixwall.pffw.MainActivity.fragment;

public class LogFilePickerDialog extends DialogFragment {

    // ATTENTION: Init to "", not null, because we use empty string to fetch the default file
    private String mLogFile = "";

    private final ArrayList<String> mLogFileOpts = new ArrayList<>();
    private final HashMap<String, String> mLogFileOpts2Files = new HashMap<>();

    private String mLastSelectedLogFileOpt = "";
    private JSONObject mJsonLogFileList = new JSONObject();

    private String mSelectedOpt = null;
    private ListView optionsListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.logfile_picker, container);
        view.findViewById(R.id.cancelButton).setOnClickListener(onClickListener);
        view.findViewById(R.id.okButton).setOnClickListener(onClickListener);

        updateLogFileLists();

        optionsListView = view.findViewById(R.id.options);

        // Do not use simple_list_item_1 here, it has a too large padding around itself
        optionsListView.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, mLogFileOpts));
        optionsListView.setSelector(R.color.cardview_shadow_start_color);
        optionsListView.setOnItemClickListener(mItemClickedHandler);

        return view;
    }

    private final AdapterView.OnItemClickListener mItemClickedHandler = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            mSelectedOpt = (String) optionsListView.getItemAtPosition(position);
            // TODO: Check why getSelectedItem() returns null
            //Toast.makeText(getActivity(), "Selected: " + optionsListView.getSelectedItem(), Toast.LENGTH_SHORT).show();
        }
    };

    private final View.OnClickListener onClickListener = (new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.cancelButton) {
                dismiss();
            } else if (view.getId() == R.id.okButton) {
                sendBackResult();
            }
        }
    });

    /**
     * Defines the listener interface
     */
    public interface LogFilePickerDialogListener {
        void onSelection(String selectedOpt, String fileName);
    }

    private void sendBackResult() {
        if (mSelectedOpt != null) {
            // TODO: Check why getParentFragment() and getTargetFragment() do not work here
            //StatsHourlyDatePickerDialogListener listener = (StatsHourlyDatePickerDialogListener) getParentFragment();
            LogFilePickerDialogListener listener = (LogFilePickerDialogListener) fragment;
            if (!mLastSelectedLogFileOpt.equals(mSelectedOpt)) {
                mLogFile = mLogFileOpts2Files.get(mSelectedOpt);
                mLastSelectedLogFileOpt = mSelectedOpt;
                listener.onSelection(mSelectedOpt, mLogFile);
            }
            dismiss();
        }
    }

    public void setArguments(String logFile, JSONObject logFileList) {
        mLogFile = logFile;
        mJsonLogFileList = logFileList;
    }

    /**
     * Update log files list to display to the user.
     * We return the selection so that the main fragment displays it as the selected log file.
     * We cannot simply return the log file name (which the fragment already knows), because
     * the selected option has an extra info about the start date of the logs in that file.
     *
     * @return The selected option.
     */
    public String updateLogFileLists() {
        mLogFileOpts.clear();
        mLogFileOpts2Files.clear();

        // ATTENTION: This is not redundant.
        // Clone to create a local copy, because we modify this local copy below
        @SuppressWarnings("RedundantStringConstructorCall")
        String logFile = new String(mLogFile);

        Iterator<String> it = mJsonLogFileList.keys();
        while (it.hasNext()) {
            String file = it.next();
            String optFileBasename = new File(file).getName();

            String opt = mJsonLogFileList.optString(file) + " - " + optFileBasename;
            mLogFileOpts.add(opt);
            mLogFileOpts2Files.put(opt, file);

            // XXX: Need the inverse of mLogFileOpts2Files list to get mLastSelectedLogFileOpt easily
            // ATTENTION: But the keys of the inverse list are not suitable, because mLogFile may refer to a tmp file: /var/tmp/pffw/logs/Pf/pflog
            // Hence we get a null mLastSelectedLogFileOpt with the following code.
            //HashBiMap<String, String> files2LogFileOpts = HashBiMap.create(mLogFileOpts2Files);
            //mLastSelectedLogFileOpt = files2LogFileOpts.inverse().get(mLogFile);

            if (file.matches(".*\\.gz$")) {
                // logFile does not have .gz extension, because it points to the file decompressed by the controller
                // Update this local copy for comparison and to print it below
                String logFileBasename = new File(logFile).getName();
                logFile += ((logFileBasename + ".gz").equals(optFileBasename)) ? ".gz" : "";
            }

            if (optFileBasename.equals(new File(logFile).getName())) {
                mLastSelectedLogFileOpt = opt;
            }
        }
        return mLastSelectedLogFileOpt;
    }
}

