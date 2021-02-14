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

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;

import java.util.Calendar;

import static org.comixwall.pffw.MainActivity.fragment;

public class StatsTimePickerDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // Use the current time as the default value for the picker
        final Calendar c = Calendar.getInstance();
        int minute = c.get(Calendar.MINUTE);

        int hour = getArguments().getInt("hour");

        TimePickerDialog.OnTimeSetListener listener = (TimePickerDialog.OnTimeSetListener) fragment;

        // Create a new instance of TimePickerDialog and return it
        // ATTENTION: Do not use the 24HourFormat of the phone, we are dealing with the hours on PFFW logs.
        //return new TimePickerDialog(getActivity(), listener, hour, minute, DateFormat.is24HourFormat(getActivity()));
        return new TimePickerDialog(getActivity(), listener, hour, minute, true);
    }
}
