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

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.Fragment;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Properties;

import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.MainActivity.token;
import static org.comixwall.pffw.MainActivity.sendToken;
import static org.comixwall.pffw.MainActivity.deleteToken;
import static org.comixwall.pffw.MainActivity.user;
import static org.comixwall.pffw.MainActivity.product;
import static org.comixwall.pffw.Utils.showMessage;

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

    private static Boolean mLoggedIn = false;

    public Boolean isLoggedin() {
        return mLoggedIn;
    }

    private static String mUser;
    private static String mPassword;
    private static String mHost;

    public String getHost() {
        return mHost;
    }

    private static int mPort;

    private static String mHostName;

    public String getHostname() {
        return mHostName;
    }

    private Session session = null;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Authenticate the given user on the given host.
     * <p>
     * PFFW users are system users. So we try to log in to the PFFW system via SSH.
     * If the session is successfully established, then the user must be authenticated.
     *
     * @throws Exception
     */
    public void login(String user, String password, String host, int port) throws Exception {
        mUser = user;
        mPassword = password;
        mHost = host;
        mPort = port;

        if (!mLoggedIn) {
            mHostName = "";

            if (createSession(mUser, mPassword, mHost, mPort)) {
                String output = runSSHCommand(new JSONArray().put("en_EN").put("system").put("GetMyName").toString());
                mHostName = new JSONArray(output).get(0).toString().trim();
            }

            mLoggedIn = session.isConnected();
            logger.finest("Controller login mHostName= " + mHostName);
        }
    }

    /**
     * Log user out by unsetting mLoggedIn.
     */
    public void logout() {
        mLoggedIn = false;
        mHostName = "";
    }

    /**
     * ATTENTION: Always disconnect the session while logging out.
     * Otherwise, we remain logged in until the session gets disconnected (times out)
     */
    private void finishLogout() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /**
     * Execute the given command.
     * We first establish a session if no session exists (the session established during login may
     * have already dropped by now), then open a channel and execute the command.
     * <p>
     * The return value of all commands on PFFW is always a json array, containing the command output,
     * error message, and the exit status of the command execution, in that order.
     *
     * @param model The model class in the PHP code.
     * @param cmd   The model command.
     * @param args  The model arguments.
     * @return Execution result in a json array.
     * @throws Exception
     */
    public String execute(String model, String cmd, Object... args) throws Exception {
        // Keep these lines here for logger
        JSONArray cmdLine = new JSONArray().put("en_EN").put(model).put(cmd);
        for (Object a : args) {
            // @attention Always call toString(), otherwise JSONObject() vars are not properly stringered
            cmdLine.put(a.toString());
        }

        logger.finest("Controller execute cmdLine= " + cmdLine.toString());

        String output = "";
        if (createSession(mUser, mPassword, mHost, mPort)) {
            // First run the token commands, if requested
            if (deleteToken) {
                runTokenCommand("DelNotifierUser", token);
                deleteToken = false;
                finishLogout();
                // Throw exception to stop executing the rest of the commands, just show the login page
                throw new Exception("Logout finished");
            }

            if (sendToken) {
                JSONObject tokenUser = new JSONObject().put(token, user + ", " + product);
                runTokenCommand("AddNotifierUser", tokenUser.toString());
                sendToken = false;
            }

            // Next run the actual command
            output = runSSHCommand(cmdLine.toString());
        }

        logger.finest("Controller execute output= " + output);

        if (!output.isEmpty()) {
            JSONArray jsonArray = new JSONArray(output);
            if (jsonArray.getInt(2) == 1) {
                throw new Exception(jsonArray.get(1).toString());
            }
        }
        return output;
    }

    private void runTokenCommand(String cmd, String arg) throws Exception {
        String output = runSSHCommand(new JSONArray().put("en_EN").put("system").put(cmd).put(arg).toString());
        String result = new JSONArray(output).get(2).toString();
        if (result.equals("0")) {
            logger.finest("Controller runTokenCommand " + cmd + "= " + arg);
        } else {
            logger.warning("Controller runTokenCommand " + cmd + " failed: " + arg);
        }
    }

    /**
     * Establish a session if none exists.
     */
    private Boolean createSession(String username, String password, String hostname, int port) throws Exception {

        if (session == null || !session.isConnected()) {
            logger.finest("Controller createSession: " + username + ", " + password + ", " + hostname + ", " + port);

            session = new JSch().getSession(username, hostname, port);

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

    /**
     * Run the command after opening a channel.
     * Note that we use an exec channel not a shell one to run the command. This channel is
     * closed after the output is received completely.
     * <p>
     * We wait for command output for a limited time only, otherwise we may get stuck here.
     *
     * @param cmd The command to run.
     * @return The command output.
     * @throws Exception
     */
    private String runSSHCommand(String cmd) throws Exception {
        String out = "";

        Channel channel = session.openChannel("exec");

        ((ChannelExec) channel).setCommand(cmd);

        InputStream in = channel.getInputStream();

        logger.fine("Controller channel connect");
        channel.connect();

        byte[] tmp = new byte[100000];

        // In nanosecs for use with System.nanoTime()
        final long ONE_SEC = 1000000000;

        // Wait 30 secs for output
        final long TIMEOUT = 30 * ONE_SEC;

        long startTime = System.nanoTime();

        while (true) {
            logger.finest("Controller get data outer loop, time to timeout (ms)= " + ((TIMEOUT + startTime - System.nanoTime()) / 1000000));

            boolean received = false;

            while (in.available() > 0) {
                logger.finest("Controller get data inner loop");

                int i = in.read(tmp, 0, 100000);
                if (i < 0) {
                    break;
                }

                out += new String(tmp, 0, i);
                // Will be set on each loop iteration unnecessarily, but ok
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
                // Reset timeout
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

/**
 * This task class is used to run commands asynchronously on a separate thread.
 * ATTENTION: The most important feature of this task class is the mIsRunning field, which ensures
 * that we do not start more than one such controller thread at one time.
 */
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
            showMessage(owner, "Controller task is running");
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
        // ATTENTION: Make sure the fragment still has a context
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
