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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.util.Collection;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.TrustManager;

public class TrustManagerFactory {

    private static final String PKIX = "PKIX";
    private static final String TLS12 = "TLSv1.2";
    private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";
    private static final String CERTIFICATE_LABEL = "CERTIFICATE";
    private static final KeyStore DEFAULT_CA_KEYSTORE = loadDefaultCAKeystore();

    private static final CertificateFactory X509_CERTIFICATE_FACTORY;

    static {
        try {
            X509_CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (final CertificateException e) {
            throw new Error("FATAL: failed to load X.509 certificate factory", e);
        }
    }

    static TrustManager[] newTrustManager(@Nullable final String trustedCertificates,
            final Supplier<KeyStore> keyStoreSupplier)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException,
            InvalidAlgorithmParameterException {
        final javax.net.ssl.TrustManagerFactory trustManagerFactory =
                javax.net.ssl.TrustManagerFactory.getInstance(PKIX);
        if (trustedCertificates != null) {
            final KeyStore keystore = keyStoreSupplier.get();
            final Collection<? extends Certificate> caCerts;
            final byte[] caCertsPem = trustedCertificates.getBytes(StandardCharsets.US_ASCII);
            caCerts = X509_CERTIFICATE_FACTORY.generateCertificates(new ByteArrayInputStream(caCertsPem));
            long cnt = 0;
            for (final Certificate caCert : caCerts) {
                keystore.setCertificateEntry("ca-" + cnt++, caCert);
            }
            trustManagerFactory.init(keystore);
            // TODO: consider adding cert revocation checker if AWS-IoT has OSCP/CRL.
        } else {
            // standard CAs; add revocation check
            final PKIXRevocationChecker revocationChecker =
                    (PKIXRevocationChecker) CertPathBuilder.getInstance(PKIX).getRevocationChecker();
            final PKIXBuilderParameters parameters =
                    new PKIXBuilderParameters(DEFAULT_CA_KEYSTORE, new X509CertSelector());
            parameters.addCertPathChecker(revocationChecker);
            trustManagerFactory.init(new CertPathTrustManagerParameters(parameters));
        }
        return trustManagerFactory.getTrustManagers();
    }

    static KeyStore emptyKeyStore()
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        // initialize an empty keystore
        keyStore.load(null, null);
        return keyStore;
    }

    private static KeyStore loadDefaultCAKeystore() {
        try {
            final String javaHome = System.getProperty("java.home");
            final String cacerts = javaHome + "/lib/security/cacerts";
            final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (final FileInputStream cacertsStream = new FileInputStream(cacerts)) {
                keystore.load(cacertsStream, "changeit".toCharArray());
            }
            return keystore;
        } catch (final KeyStoreException e) {
            throw new Error("FATAL: Cannot create default CA keystore");
        } catch (final IOException e) {
            throw new Error("FATAL: Cannot read default CA keystore");
        } catch (final NoSuchAlgorithmException | CertificateException e) {
            throw new Error("FATAL: Cannot load default CA keystore");
        }
    }

}
