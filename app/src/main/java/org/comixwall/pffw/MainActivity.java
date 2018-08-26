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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static Cache cache;

    public static Controller controller;
    private static boolean boundToController = false;

    // Firebase token handling
    public static String token = "";
    public static boolean sendToken = false;
    public static boolean deleteToken = false;

    /**
     * Used to get the constructor of and instantiate the fragment class referred by the menu item selected.
     */
    private HashMap<Integer, Class> mMenuItems2Fragments;
    public static Fragment fragment = new Fragment();

    public LogFilePickerDialog logFilePickerDialog;

    public ActionBarDrawerToggle toggle;
    public DrawerLayout drawer;

    private NavigationView navigationView;

    public static final Logger logger;

    private Menu optionsMenu;
    private static int lastNotificationTimestamp = 0;

    static {
        logger = Logger.getLogger("org.comixwall.PFFW");

        Level mLogLevel = Level.ALL;
        logger.setLevel(mLogLevel);

        // TODO: setUseParentHandlers() Does not seem to have any effect
        //logger.setUseParentHandlers(true);

        if (logger.getHandlers().length == 0) {
            Handler ch = new ConsoleHandler();
            ch.setLevel(mLogLevel);
            logger.addHandler(ch);
        }

        System.out.println("Created logger with level: " + logger.getLevel());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TODO: Check why main activity is recreated on rotation
        logger.finest("MainActivity onCreate()");

        mMenuItems2Fragments = new HashMap<Integer, Class>() {{
            put(R.id.menuNotifications, Notifications.class);
            // ATTENTION: InfoPf fragment should never be instantiated using this map
            put(R.id.menuInfoPf, InfoPf.class);
            put(R.id.menuInfoSystem, InfoSystem.class);
            put(R.id.menuInfoHosts, InfoHosts.class);
            put(R.id.menuInfoIfs, InfoIfs.class);
            put(R.id.menuInfoRules, InfoRules.class);
            put(R.id.menuInfoStates, InfoStates.class);
            put(R.id.menuInfoQueues, InfoQueues.class);
            put(R.id.menuStatsGeneral, StatsGeneral.class);
            put(R.id.menuStatsDaily, StatsDaily.class);
            put(R.id.menuStatsHourly, StatsHourly.class);
            put(R.id.menuStatsLive, StatsLive.class);
            put(R.id.menuGraphsInterfaces, GraphsIfs.class);
            put(R.id.menuGraphsTransfer, GraphsTransfer.class);
            put(R.id.menuGraphsStates, GraphsStates.class);
            put(R.id.menuGraphsMbufs, GraphsMbufs.class);
            put(R.id.menuLogsArchives, LogsArchives.class);
            put(R.id.menuLogsLive, LogsLive.class);
        }};

        FragmentManager fm = getSupportFragmentManager();

        // ATTENTION: Always load cache from the FragmentManager.
        // Otherwise, the cache survives even after the application is closed by the last Back button press
        cache = (Cache) fm.findFragmentByTag("cache");
        if (cache == null) {
            // create the fragment and data the first time
            cache = new Cache();
            fm.beginTransaction().add(cache, "cache").commit();
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawerLayout);

        toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = findViewById(R.id.navView);
        navigationView.setNavigationItemSelectedListener(this);

        logFilePickerDialog = new LogFilePickerDialog();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (controller == null || !controller.isLoggedin()) {
            // ATTENTION: Login fragment should be inflated only after SSH session is created first
            // onResume() is executed after onRestoreInstanceState().
            FragmentManager fm = getSupportFragmentManager();
            android.support.v4.app.FragmentTransaction transaction = fm.beginTransaction();

            fragment = new Login();
            // ATTENTION: Do not add but replace, because login page may be rotated, which brings us here again.
            //transaction.add(R.id.fragmentContainer, fragment, "MainFragment");
            transaction.replace(R.id.fragmentContainer, fragment);
            transaction.commit();
        } else {
            showFirstFragment();
        }
    }

    public void showFirstFragment() {
        NavigationView navigationView = findViewById(R.id.navView);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        if (bundle != null && bundle.containsKey("title") && bundle.containsKey("body") && bundle.containsKey("data")) {
            // If there is a notification bundle, show the notification fragment
            try {
                JSONObject data = new JSONObject(bundle.getString("data"));

                // ATTENTION: Timestamp check is a workaround for the case the user clicks the Overview button
                // If the activity was created with a notification intent while the app was in the background,
                // closing the app and then pressing the Overview button recreates the activity with the same intent,
                // hence we reach here and add the same notification one more time.
                // Timestamp is a unique notification id to prevent such mistakes
                // TODO: Find a way to fix this Overview button issue
                int timestamp = Integer.parseInt(data.getString("timestamp"));
                if (lastNotificationTimestamp < timestamp) {
                    lastNotificationTimestamp = timestamp;

                    Notifications.addNotification(Notification.newInstance(data));

                    // Remove one of the extras, so we don't add the same notification again
                    intent.removeExtra("title");
                    setIntent(new Intent());
                } else {
                    logger.finest("showFirstFragment will not process the same notification: " + lastNotificationTimestamp);
                }
            } catch (Exception e) {
                logger.warning("showFirstFragment Exception= " + e.getMessage());
            }
            // Reset the fragment, so onNavigationItemSelected() displays the Notifications fragment in any case
            fragment = new Fragment();
            onNavigationItemSelected(navigationView.getMenu().findItem((R.id.menuNotifications)));
        } else {
            // Avoid blank pages by showing InfoPf fragment if the backstack is empty
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                // Reset the fragment, so onNavigationItemSelected() displays the InfoPf fragment in any case
                fragment = new Fragment();
                onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menuInfoPf));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();

            if (controller != null && controller.isLoggedin()) {
                // Avoid blank pages by showing InfoPf fragment if the backstack is empty
                // ATTENTION: This may show InfoPf for a brief period of time, if the super.onBackPressed()
                // call above is going to close the activity after returning from this function
                if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                    // Reset the fragment, so onNavigationItemSelected() displays the InfoPf fragment in any case
                    fragment = new Fragment();
                    onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menuInfoPf));
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu;
        // Inflate the menu; this adds items to the app bar if it is present.
        // Login fragment should not have any appbar menu
        if (fragment.getClass() != Login.class) {
            getMenuInflater().inflate(R.menu.app_bar_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle time bar item clicks here.
        int id = item.getItemId();

        if (id == R.id.menuRefresh) {
            // Refresh requests are handled by fragments
            return fragment.onOptionsItemSelected(item);
        } else if (id == R.id.menuLogout) {
            logger.finest("onOptionsItemSelected recreate()");

            // TODO: Do we need to reset the backstack if we are going to recreate the activity next?
            popAllBackStack();

            // Raise the deleteToken flag and refresh the fragment, so its resume method runs delToken() in Controller.execute()
            deleteToken = true;
            try {
                // Throws exception to stop further command execution after deleting token
                // ATTENTION: All fragments should have the Refresh menu item for this workaround to work
                fragment.onOptionsItemSelected(optionsMenu.findItem(R.id.menuRefresh));
            } catch (Exception ignored) {}

            controller.logout();

            // We recreate the activity so that onCreate() resets everything and
            // onResume() displays the Login fragment.
            recreate();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Select the fragment to display.
     * We modify the backstack ourselves so that no fragment is pushed to the backstack twice.
     * So if a fragment which is already in the backstack is selected, we roll back the backstack
     * to its position.
     * <p>
     * We never push the InfoPf fragment to the backstack because it is always
     * the first fragment displayed (if we push it to the backstack too, pressing the back button
     * while InfoPf fragment is displayed causes a blank activity screen).
     *
     * @param item The menu item selected.
     * @return See {@link NavigationView.OnNavigationItemSelectedListener}
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        // Ignore requests for the same fragment already displayed
        if (!mMenuItems2Fragments.get(id).isInstance(fragment)) {
            FragmentManager fm = getSupportFragmentManager();

            boolean add = true;

            if (id == R.id.menuInfoPf) {
                // InfoPf is the main fragment, should never be removed,
                // so remove all backstack entries first to reach the first InfoPf.
                popAllBackStack();

                // Never add InfoPf to the backstack
                add = false;
                fragment = new InfoPf();

                // ATTENTION: menuInfoPf does not check initially, so we need to manage it ourselves
                item.setChecked(true);
            } else {
                // TODO: Check why android:checkableBehavior="single" does not uncheck menuInfoPf
                MenuItem itemInfoPf = navigationView.getMenu().findItem(R.id.menuInfoPf);
                if (itemInfoPf.isChecked()) {
                    itemInfoPf.setChecked(false);
                }

                try {
                    fragment = (Fragment) mMenuItems2Fragments.get(id).getConstructor().newInstance();
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.severe("EXCEPTION: " + e.toString());
                    return false;
                }
            }

            String fragmentName = fragment.getClass().getSimpleName();

            // Rolls back the backstack if the fragment is already in
            if (!fm.popBackStackImmediate(fragmentName, 0)) {
                android.support.v4.app.FragmentTransaction transaction = fm.beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

                if (add) {
                    transaction.addToBackStack(fragmentName);
                }

                // TODO: Check if we need to pass any args
                //fragment.setArguments(getIntent().getExtras());
                transaction.replace(R.id.fragmentContainer, fragment);

                transaction.commit();
            }
        } else {
            logger.finest("onNavigationItemSelected will not show the same fragment");
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void popAllBackStack() {
        // TODO: What is the best way to pop all backstack items?
        // It does NOT help to use a special StackBottom name while adding the first InfoPf to the backstack,
        // then we could pop all items simply by:
        // fm.popBackStackImmediate("StackBottom", FragmentManager.POP_BACK_STACK_INCLUSIVE);
        // But if we add the first InfoPf to the backstack, we get a blank page when the user presses the back button.

        FragmentManager fm = getSupportFragmentManager();
        while (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to Controller
        Intent intent = new Intent(this, Controller.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the service
        if (boundToController) {
            unbindService(mConnection);
            boundToController = false;
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to Controller, cast the IBinder and get Controller instance
            controller = ((Controller.ControllerBinder) service).getService();
            boundToController = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            boundToController = false;
        }
    };
}

