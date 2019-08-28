/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.internal.ssl;

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

import javax.annotation.Nullable;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Creates {@link javax.net.ssl.TrustManagerFactory}s from different sources.
 */
public final class TrustManagerFactoryFactory {

    private static final String PKIX = "PKIX";
    private static final KeyStore DEFAULT_CA_KEYSTORE = loadDefaultCAKeystore();

    private static final CertificateFactory X509_CERTIFICATE_FACTORY;
    private final KeyStoreFactory keyStoreFactory;
    private final ExceptionMapper exceptionMapper;

    static {
        try {
            X509_CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (final CertificateException e) {
            throw new Error("FATAL: failed to load X.509 certificate factory", e);
        }
    }

    TrustManagerFactoryFactory(final ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
        keyStoreFactory = new KeyStoreFactory(exceptionMapper);
    }

    public static TrustManagerFactoryFactory getInstance() {
        return new TrustManagerFactoryFactory(new ExceptionMapper(DittoHeaders.empty()));
    }

    public TrustManagerFactory newTrustManagerFactory(@Nullable final String trustedCertificates) {
        return exceptionMapper.handleExceptions(() -> createTrustManagerFactory(trustedCertificates));
    }

    public TrustManagerFactory newTrustManagerFactory(final Connection connection) {
        final String trustedCertificates = connection.getTrustedCertificates().orElse(null);
        return exceptionMapper.handleExceptions(() -> createTrustManagerFactory(trustedCertificates));
    }

    public TrustManagerFactory newInsecureTrustManagerFactory() {
        return InsecureTrustManagerFactory.INSTANCE;
    }

    private TrustManagerFactory createTrustManagerFactory(@Nullable final String trustedCertificates)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException,
            InvalidAlgorithmParameterException {
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(PKIX);
        if (trustedCertificates != null) {
            final KeyStore keystore = keyStoreFactory.newKeystore();
            final Collection<? extends Certificate> caCerts;
            final byte[] caCertsPem = trustedCertificates.getBytes(StandardCharsets.US_ASCII);
            caCerts = X509_CERTIFICATE_FACTORY.generateCertificates(new ByteArrayInputStream(caCertsPem));
            long cnt = 0;
            for (final Certificate caCert : caCerts) {
                keystore.setCertificateEntry("ca-" + cnt++, caCert);
            }
            trustManagerFactory.init(keystore);
        } else {
            // standard CAs; add revocation check
            final PKIXRevocationChecker revocationChecker =
                    (PKIXRevocationChecker) CertPathBuilder.getInstance(PKIX).getRevocationChecker();
            final PKIXBuilderParameters parameters =
                    new PKIXBuilderParameters(DEFAULT_CA_KEYSTORE, new X509CertSelector());
            parameters.addCertPathChecker(revocationChecker);
            trustManagerFactory.init(new CertPathTrustManagerParameters(parameters));
        }
        return trustManagerFactory;
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