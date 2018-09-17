/*
 * Copyright (C) 2017-2018 Soner Tari
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

import java.util.Timer;
import java.util.TimerTask;

class RefreshTimer extends Timer {

    public interface OnTimeoutListener {
        void onTimeout();
    }

    private class RefreshTask extends TimerTask {

        public void run() {
            mActivity.runOnUiThread(mRunRefresh);
        }
    }

    private final MainActivity mActivity;
    private final OnTimeoutListener mTimeoutListener;

    public RefreshTimer(MainActivity activity, final OnTimeoutListener listener) {
        mActivity = activity;
        mTimeoutListener = listener;
    }

    private final Runnable mRunRefresh = new Runnable() {
        public void run() {
            mTimeoutListener.onTimeout();
        }
    };

    public void start(int timeout) {
        final int ONE_SECOND = 1000;
        schedule(new RefreshTask(), timeout * ONE_SECOND, timeout * ONE_SECOND);
    }
}
