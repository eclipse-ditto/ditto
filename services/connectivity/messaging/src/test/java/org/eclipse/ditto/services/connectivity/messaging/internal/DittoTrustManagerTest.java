/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.connectivity.messaging.internal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates;
import org.junit.Test;

public class DittoTrustManagerTest {

    private static final String AWS_IOT_ENDPOINT = "aws-account-id.iot.eu-central-1.amazonaws.com";
    private static final CertificateFactory X509_CERTIFICATE_FACTORY;
    private static final String AUTH_TYPE = "RSA";

    static {
        try {
            X509_CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (final CertificateException e) {
            throw new Error("FATAL: failed to load X.509 certificate factory", e);
        }
    }

    @Test
    public void testAwsIotCertificates() throws Exception {

        final KeyStore keyStore = TrustManagerFactory.emptyKeyStore();

        final TrustManager[] trustManagers =
                TrustManagerFactory.newTrustManager(Certificates.AWS_CA_CRT, () -> keyStore);

        final List<X509TrustManager> dittoTrustManagers =
                Stream.of(DittoTrustManager.wrapTrustManagers(trustManagers,
                        AWS_IOT_ENDPOINT)).map(tm -> ((X509TrustManager) tm)).collect(Collectors.toList());

        final byte[] bytes = Certificates.AWS_IOT_CRT.getBytes(StandardCharsets.US_ASCII);


        final X509Certificate[] x509Certificates =
                X509_CERTIFICATE_FACTORY.generateCertificates(new ByteArrayInputStream(bytes))
                        .stream()
                        .map(crt -> ((X509Certificate) crt))
                        .toArray(X509Certificate[]::new);

        for (final X509TrustManager dtm : dittoTrustManagers) {
            dtm.checkServerTrusted(x509Certificates, AUTH_TYPE);
        }
    }
}