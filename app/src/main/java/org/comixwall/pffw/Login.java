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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.test.espresso.core.deps.guava.hash.HashCode;
import android.support.test.espresso.core.deps.guava.hash.Hashing;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;

import java.nio.charset.Charset;

import static org.comixwall.pffw.MainActivity.controller;
import static org.comixwall.pffw.MainActivity.fragment;
import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.Utils.processException;
import static org.comixwall.pffw.Utils.showMessage;

public class Login extends Fragment implements ControllerTask.ControllerTaskListener {

    private ProgressBar pbProgress;

    private AutoCompleteTextView tvUser;
    private TextInputEditText etPassword;
    private TextInputEditText etHost;
    private TextInputEditText etPort;

    private String mUser;
    private String mPassword;
    private String mHost;
    private int mPort;

    private Boolean mLoggedIn = false;

    private String mLastError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.login, container, false);

        pbProgress = (ProgressBar) view.findViewById(R.id.progress);

        tvUser = (AutoCompleteTextView) view.findViewById(R.id.user);
        etPassword = (TextInputEditText) view.findViewById(R.id.password);
        etHost = (TextInputEditText) view.findViewById(R.id.host);
        etPort = (TextInputEditText) view.findViewById(R.id.port);

        Button btnButton = (Button) view.findViewById(R.id.button);
        btnButton.setOnClickListener(mButtonClickHandler);

        // Disable drawer and toggle button, appbar menu items are not created if the fragment is Login
        ((MainActivity) getActivity()).drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        ((MainActivity) getActivity()).toggle.setDrawerIndicatorEnabled(false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        fragment = this;
    }

    @Override
    public void executePreTask() {
    }

    @Override
    public void preExecute() {
        pbProgress.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean executeTask() {
        try {
            logger.finest("setAuthParams: " + mUser + ", " + mPassword + ", " + mHost + ", " + mPort);
            controller.setAuthParams(mUser, mPassword, mHost, mPort);

            mLoggedIn = controller.login();

        } catch (Exception e) {
            mLastError = processException(e);
            return false;
        }
        return true;
    }

    @Override
    public void postExecute(boolean result) {
        if (result) {
            processLogin();
        } else {
            showMessage(this, "Error: " + mLastError);
        }

        pbProgress.setVisibility(View.GONE);
    }

    @Override
    public void executeOnCancelled() {
        pbProgress.setVisibility(View.GONE);
    }

    private void processLogin() {

        ((MainActivity) getActivity()).setLoggedIn(mLoggedIn);

        if (mLoggedIn) {
            fragment = new InfoPf();
            FragmentManager fm = getActivity().getSupportFragmentManager();
            android.support.v4.app.FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(R.id.fragmentContainer, fragment);
            transaction.commit();

            // Enable drawer and toggle button
            ((MainActivity) getActivity()).drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            ((MainActivity) getActivity()).toggle.setDrawerIndicatorEnabled(true);
            // Recreate menu items, the fragment is InfoPf now
            getActivity().invalidateOptionsMenu();
        }
    }

    private void login() {
        ControllerTask.run(this, this);
    }

    private final View.OnClickListener mButtonClickHandler = new View.OnClickListener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onClick(View view) {
            int id = view.getId();

            if (id == R.id.button) {
                mUser = tvUser.getText().toString();

                // ATTENTION: Encrypt the password immediately.
                HashCode hashCode = Hashing.sha1().hashString(etPassword.getText().toString(), Charset.defaultCharset());
                mPassword = hashCode.toString();

                mHost = etHost.getText().toString();

                boolean applyDefaultPort = false;
                try {
                    mPort = Integer.parseInt(etPort.getText().toString());
                    if (mPort < 1 || mPort > 65535) {
                        applyDefaultPort = true;
                    }
                } catch (Exception e) {
                    applyDefaultPort = true;
                }

                if (applyDefaultPort) {
                    mPort = 22;
                    etPort.setText(Integer.toString(mPort));
                }

                login();
            }
        }
    };
}

