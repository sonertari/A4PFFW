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

import androidx.fragment.app.Fragment;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.PublicKey;
import java.security.Security;
import java.util.concurrent.TimeUnit;

import static org.comixwall.pffw.MainActivity.deleteToken;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.MainActivity.product;
import static org.comixwall.pffw.MainActivity.sendToken;
import static org.comixwall.pffw.MainActivity.token;
import static org.comixwall.pffw.MainActivity.user;
import static org.comixwall.pffw.Utils.showMessage;

public class Controller extends Service {
    // This is to fix: net.schmizz.sshj.transport.TransportException: no such algorithm: ECDSA for provider BC
    // https://stackoverflow.com/questions/26653399/android-sshj-exception-upon-connect-keyfactory-ecdsa-implementation-not-fou
    static {
        Security.removeProvider("BC"); // first remove default os provider
        Security.insertProviderAt(new BouncyCastleProvider(), 1); // add new provider
    }

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

    private SSHClient ssh = null;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Authenticate the given user on the given host.
     * <p>
     * PFFW users are system users. So we try to log in to the PFFW system via SSH.
     * If the ssh connection is successfully established, the user must be authenticated.
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

            if (createSsh(mUser, mPassword, mHost, mPort)) {
                String output = runSSHCommand(new JSONArray().put("en_EN").put("system").put("GetMyName").toString());
                mHostName = new JSONArray(output).get(0).toString().trim();
            }

            mLoggedIn = ssh != null && ssh.isConnected();
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
     * ATTENTION: Always disconnect the ssh connection while logging out.
     * Otherwise, we remain logged in until the ssh connection gets disconnected (times out)
     */
    private void finishLogout() {
        try {
            if (ssh != null && ssh.isConnected()) {
                ssh.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Execute the given command.
     * We first try to establish an ssh connection if one does not exist (the ssh connection
     * established during login may have already dropped by now), then start a session and execute
     * the command.
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
        if (createSsh(mUser, mPassword, mHost, mPort)) {
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
     * Establish an ssh connection if none exists.
     */
    private Boolean createSsh(String username, String password, String hostname, int port) throws Exception {

        if (ssh == null || !ssh.isConnected()) {
            logger.finest("Controller createSsh: " + username + ", " + password + ", " + hostname + ", " + port);

            ssh = new SSHClient();

            // Avoid asking for key confirmation
            HostKeyVerifier hostKeyVerifier = new HostKeyVerifier() {
                @Override
                public boolean verify(String hostname, int port, PublicKey key) {
                    return true;
                }
            };
            ssh.addHostKeyVerifier(hostKeyVerifier);

            logger.info("Controller ssh connect");
            ssh.connect(hostname, port);
            ssh.authPassword(username, password);

            // Wait 30 secs for connection establishment
            ssh.setTimeout(30);
        } else {
            logger.fine("Controller ssh already connected");
        }

        return ssh.isConnected();
    }

    /**
     * Run the command after opening a session.
     * Note that we use an exec channel not a shell one to run the command. This channel is
     * closed after the output is received completely.
     * <p>
     * We wait for command output for a limited time only, otherwise we may get stuck here.
     *
     * @param command The command to run.
     * @return The command output.
     * @throws Exception
     */
    private String runSSHCommand(String command) throws Exception {
        String out = "";

        Session session = null;
        try {
            session = ssh.startSession();
            Session.Command cmd = session.exec(command);

            out = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(30, TimeUnit.SECONDS);

            logger.finest("Controller runSSHCommand cmd getExitStatus= " + cmd.getExitStatus());
        } finally {
            try {
                if (session != null) {
                    session.close();
                }
            } catch (Exception ignored) {
            }
        }
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
