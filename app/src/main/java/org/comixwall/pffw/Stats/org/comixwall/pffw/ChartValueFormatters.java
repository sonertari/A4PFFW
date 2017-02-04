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

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.text.DecimalFormat;
import java.util.HashMap;

class AxisValueFormatter implements IAxisValueFormatter
{
    private final DecimalFormat mFormat;

    AxisValueFormatter() {
        mFormat = new DecimalFormat("###,###,###,##0");
    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        return mFormat.format(value);
    }
}

class XAxisDailyValueFormatter implements IAxisValueFormatter
{
    private final DecimalFormat mFormat;
    private HashMap<Float, String> mLabels;

    XAxisDailyValueFormatter() {
        mFormat = new DecimalFormat("###,###,###,##0");
    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        if (mLabels == null) {
            return mFormat.format(value);
        } else if (mLabels.get(value) == null) {
            return mFormat.format(value);
        } else {
            return mLabels.get(value);
        }
    }

    void setLabels(HashMap<Float, String> labels) {
        mLabels = labels;
    }
}
