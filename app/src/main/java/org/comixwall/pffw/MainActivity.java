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

import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static Cache cache;

    public static Controller controller;
    private static boolean boundToController = false;

    private HashMap<Integer, Class> mMenuItems2Fragments;
    public static Fragment fragment = new Fragment();

    public LogFilePickerDialog logFilePickerDialog;

    public ActionBarDrawerToggle toggle;
    public DrawerLayout drawer;

    private Boolean mLoggedIn = false;

    public void setLoggedIn(Boolean loggedIn) {
        mLoggedIn = loggedIn;
    }

    public static final Logger logger;

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
            // ATTENTION: InfoPf fragment should never be instantiated using this map
            //put(R.id.menuInfoPf, InfoPf.class);
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
        /// Otherwise, the cache survives even after the application is closed by the last Back button press
        cache = (Cache) fm.findFragmentByTag("cache");
        if (cache == null) {
            // create the fragment and data the first time
            cache = new Cache();
            fm.beginTransaction().add(cache, "cache").commit();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = (DrawerLayout) findViewById(R.id.drawerLayout);

        toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.navView);
        navigationView.setNavigationItemSelectedListener(this);

        logFilePickerDialog = new LogFilePickerDialog();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        cache.bundle.putBoolean("mLoggedIn", mLoggedIn);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mLoggedIn = cache.bundle.getBoolean("mLoggedIn");
    }


    @Override
    public void onResume() {
        super.onResume();

        if (!mLoggedIn) {
            // ATTENTION: Login fragment should be inflated only after SSH session is created first
            // onResume() is executed after onRestoreInstanceState().
            FragmentManager fm = getSupportFragmentManager();
            android.support.v4.app.FragmentTransaction transaction = fm.beginTransaction();

            fragment = new Login();
            // ATTENTION: Do not add but replace, because login page may be rotated, which brings us here again.
            //transaction.add(R.id.fragmentContainer, fragment, "MainFragment");
            transaction.replace(R.id.fragmentContainer, fragment);

            transaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
            return fragment.onOptionsItemSelected(item);
        } else if (id == R.id.menuLogout) {
            logger.finest("onOptionsItemSelected recreate()");

            popAllBackStack();
            mLoggedIn = false;
            recreate();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        FragmentManager fm = getSupportFragmentManager();

        boolean add = true;

        if (id == R.id.menuInfoPf) {
            // InfoPf is the main fragment, should never be removed,
            // so remove all backstack entries first to reach the first InfoPf.
            popAllBackStack();

            // Never add InfoPf to the backstack
            add = false;
            fragment = new InfoPf();
        } else {
            try {
                fragment = (Fragment) mMenuItems2Fragments.get(id).getConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                logger.warning("EXCEPTION: " + e.toString());
                return false;
            }
        }

        String fragmentName = fragment.getClass().getSimpleName();

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
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to Controller, cast the IBinder and get Controller instance
            Controller.ControllerBinder binder = (Controller.ControllerBinder) service;
            controller = binder.getService();
            boundToController = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            boundToController = false;
        }
    };
}

