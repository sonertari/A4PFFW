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

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.util.Properties;

import static org.comixwall.pffw.MainActivity.logger;

public class Controller extends Service {
    // Binder given to clients
    private final IBinder mBinder = new ControllerBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class ControllerBinder extends Binder {
        Controller getService() {
            // Return this instance of Controller so clients can call public methods
            return Controller.this;
        }
    }

    private static String mUser;
    private static String mPassword;
    private static String mHost;
    private static int mPort;

    public void setAuthParams(String user, String password, String host, int port) {
        mUser = user;
        mPassword = password;
        mHost = host;
        mPort = port;
    }

    private Session session = null;

    private final String mPffwc = "/usr/bin/doas /var/www/htdocs/pffw/Controller/pffwc.php";
    private final String mLocale = "en_En";

    /// @attention Model may change, so do not append to the command line now
    public String model = "pf";

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public Boolean login() throws Exception {
        String output = "";
        if (createSession(mUser, mPassword, mHost, mPort)) {
            output = runSSHCommand("hostname");
        }

        logger.finest("Controller login output= " + output);
        return session.isConnected();
    }

    public String execute(String cmd, Object... args) throws Exception {
        String cmdLine = mPffwc + " " + mLocale + " " + model + " " + cmd;
        for (Object a : args) {
            cmdLine += " '" + a + "'";
        }

        logger.finest("Controller execute cmdLine= " + cmdLine);

        String output = "";
        if (createSession(mUser, mPassword, mHost, mPort)) {
            output = runSSHCommand(cmdLine);
        }

        logger.finest("Controller execute output= " + output);
        return output;
    }

    private Boolean createSession(String username, String password, String hostname, int port) throws Exception {

        if (session == null || !session.isConnected()) {
            logger.finest("Controller createSession: " + username + ", " + password + ", " + hostname + ", " + port);

            JSch jsch = new JSch();
            session = jsch.getSession(username, hostname, port);

            session.setPassword(password);

            // Avoid asking for key confirmation
            Properties prop = new Properties();
            prop.put("StrictHostKeyChecking", "no");
            session.setConfig(prop);

            // Wait 30 secs for session establishment
            session.setTimeout(30000);

            logger.info("Controller session connect");
            session.connect();
        } else {
            logger.fine("Controller session already connected");
        }

        return session.isConnected();
    }

    private String runSSHCommand(String cmd) throws Exception {
        String out = "";

        Channel channel = session.openChannel("exec");

        ((ChannelExec) channel).setCommand(cmd);

        InputStream in = channel.getInputStream();

        logger.fine("Controller channel connect");
        channel.connect();

        // In nanosecs for use with System.nanoTime()
        final long ONE_SEC = 1000000000;

        // Wait 30 secs for output
        final long TIMEOUT = 30 * ONE_SEC;

        long startTime = System.nanoTime();

        byte[] tmp = new byte[100000];

        while (true) {
            logger.finest("Controller get data outer loop, time to timeout (millis)= " + ((TIMEOUT + startTime - System.nanoTime()) / 1000000));

            boolean received = false;

            while (in.available() > 0) {
                logger.finest("Controller get data inner loop");

                int i = in.read(tmp, 0, 100000);
                if (i < 0) {
                    break;
                }

                out += new String(tmp, 0, i);
                received = true;
            }

            if (channel.isClosed()) {
                if (in.available() > 0) {
                    continue;
                }
                logger.finest("Controller runSSHCommand channel getExitStatus= " + channel.getExitStatus());
                break;
            }

            if (received) {
                startTime = System.nanoTime();
                logger.finest("Controller reset output timeout");
            } else {
                if (System.nanoTime() - startTime > TIMEOUT) {
                    channel.disconnect();
                    logger.warning("Controller output timed out");
                    throw new Exception("Controller output timed out");
                }
            }

            try {
                Thread.sleep(10);
            } catch (Exception ignored) {
            }
        }

        channel.disconnect();
        return out;
    }
}

class ControllerTask extends AsyncTask<Void, Void, Void> {
    private final Fragment owner;

    private boolean result;

    private final ControllerTaskListener controllerTaskListener;

    private static boolean mIsRunning = false;

    interface ControllerTaskListener {
        void executePreTask();

        void preExecute();

        boolean executeTask();

        void postExecute(boolean result);

        void executeOnCancelled();
    }

    static void run(final Fragment owner, final ControllerTaskListener listener) {
        if (mIsRunning) {
            Toast.makeText(owner.getContext(), "Controller task is running", Toast.LENGTH_SHORT).show();
            logger.warning("Another controller task is running, cannot run " + owner.getClass().getSimpleName());
        } else {
            mIsRunning = true;
            new ControllerTask(owner, listener).execute((Void) null);
        }
    }

    private ControllerTask(final Fragment owner, final ControllerTaskListener listener) {
        this.owner = owner;
        controllerTaskListener = listener;

        controllerTaskListener.executePreTask();
    }

    @Override
    protected Void doInBackground(Void... params) {
        result = controllerTaskListener.executeTask();
        return null;
    }

    @Override
    protected void onPreExecute() {
        controllerTaskListener.preExecute();
    }

    @Override
    protected void onPostExecute(Void v) {
        // Make sure the fragment still has a context
        if (owner.isVisible()) {
            controllerTaskListener.postExecute(result);
        } else {
            logger.info("Fragment not visible onPostExecute: " + owner.getClass().getSimpleName());
        }

        mIsRunning = false;
    }

    @Override
    protected void onCancelled() {
        controllerTaskListener.executeOnCancelled();
        mIsRunning = false;
    }
}
