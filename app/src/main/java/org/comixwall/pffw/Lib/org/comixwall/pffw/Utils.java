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

import android.support.v4.app.Fragment;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.comixwall.pffw.MainActivity.logger;

class Utils {
    static void updateStatusViews(String status, ImageView iv, TextView tv, String proc) {
        int image;
        int text;

        if (status.compareTo("0") == 0) {
            image = R.drawable.pass;
            text = R.string.proc_is_running;
        } else {
            image = R.drawable.block;
            text = R.string.proc_is_not_running;
        }

        iv.setImageResource(image);
        tv.setText(String.format(tv.getResources().getString(text), proc));
    }

    static String processException(Exception e) {

        String message;
        if (e.getCause() != null) {
            message = e.getCause().getMessage();
        } else {
            message = e.getMessage();
        }

        e.printStackTrace();
        logger.warning("Exception: " + message);

        return message;
    }

    static void showMessage(final Fragment owner, final String message) {
        // Make sure the fragment still has a context
        if (owner.isVisible()) {
            Toast.makeText(owner.getContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            logger.info("Fragment not visible on showMessage: " + owner.getClass().getSimpleName());
        }
    }

    static SSLContext getSslContext(final Fragment owner) {
        SSLContext sslContext = null;
        try {
            // Load our CA from an InputStream
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = owner.getResources().openRawResource(
                    owner.getResources().getIdentifier("server", "raw", owner.getActivity().getPackageName()));

            Certificate ca;
            try {
                ca = cf.generateCertificate(caInput);
                logger.finest("ca=" + ((X509Certificate) ca).getSubjectDN());
            } finally {
                caInput.close();
            }

            // Create a KeyStore containing our trusted CA
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CA in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("getSslContext exception: " + e.toString());
        }
        return sslContext;
    }
}
