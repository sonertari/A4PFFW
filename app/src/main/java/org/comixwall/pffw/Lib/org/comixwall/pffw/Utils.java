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

/**
 * Utilities, common methods used by various classes.
 */
class Utils {
    /**
     * Update the status image and text.
     * Used by process status views.
     *
     * @param status "0" for running, "1" for stopped.
     * @param iv ImageView to update.
     * @param tv TextView to update.
     * @param proc Process name.
     */
    static void updateStatusViews(String status, ImageView iv, TextView tv, String proc) {
        int image;
        int text;

        if (status.equals("0")) {
            image = R.drawable.pass;
            text = R.string.proc_is_running;
        } else {
            image = R.drawable.block;
            text = R.string.proc_is_not_running;
        }

        iv.setImageResource(image);
        tv.setText(String.format(tv.getResources().getString(text), proc));
    }

    /**
     * Get and log the given exception message.
     * The return value is used to update the last error to be displayed to the user.
     * <p>
     * We try to get the shortest message without package details, hence we check
     * if there is a cause first.
     *
     * @param e Exception.
     * @return Exception message.
     */
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

    /**
     * Show the given message in a toast.
     * Do not show the message if the fragment is not visible, which is possible if the user has
     * switched to another fragment by the time this method is called.
     *
     * @param owner Fragment which initiated the call to this method.
     * @param message Message to display.
     */
    static void showMessage(final Fragment owner, final String message) {
        // Make sure the fragment still has a context
        if (owner.isVisible()) {
            Toast.makeText(owner.getContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            logger.info("Fragment not visible on showMessage: " + owner.getClass().getSimpleName());
        }
    }

    /**
     * Create an SSL context which trusts the PFFW server certificate.
     * PFFW server certificate is self signed, hence is not verified by the default SSL context.
     *
     * @param owner Fragment which initiated the call to this method.
     * @return SSL context.
     */
    static SSLContext getSslContext(final Fragment owner) {
        SSLContext sslContext = null;
        try {
            // Load our crt from an InputStream
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream crtInput = owner.getResources().openRawResource(
                    owner.getResources().getIdentifier("server", "raw", owner.getActivity().getPackageName()));

            Certificate crt;
            try {
                crt = cf.generateCertificate(crtInput);
                logger.finest("server.crt=" + ((X509Certificate) crt).getSubjectDN());
            } finally {
                crtInput.close();
            }

            // Create a KeyStore containing our trusted crt
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("server.crt", crt);

            // Create a TrustManager that trusts the crt in our KeyStore
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
