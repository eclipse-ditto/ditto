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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.X509CertSelector;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Creates {@link javax.net.ssl.TrustManagerFactory}s from different sources.
 */
public final class TrustManagerFactoryFactory {

    private static final String PKIX = "PKIX";
    private static final KeyStore DEFAULT_CA_KEYSTORE = loadDefaultCAKeystore();
    private static final String CERTIFICATE_LABEL = "CERTIFICATE";

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
        this.exceptionMapper = checkNotNull(exceptionMapper, "exceptionMapper");
        ;
        keyStoreFactory = new KeyStoreFactory(exceptionMapper);
    }

    public static TrustManagerFactoryFactory getInstance(final DittoHeaders dittoHeaders) {
        return new TrustManagerFactoryFactory(ExceptionMapper.forTrustedCertificates(dittoHeaders));
    }

    public static TrustManagerFactoryFactory getInstance(final ExceptionMapper exceptionMapper) {
        return new TrustManagerFactoryFactory(exceptionMapper);
    }

    public TrustManagerFactory newTrustManagerFactory(@Nullable final String trustedCertificates,
            final boolean checkRevocation) {
        return handleExceptions(() -> createTrustManagerFactory(trustedCertificates, checkRevocation));
    }

    public TrustManagerFactory newTrustManagerFactory(final Connection connection, final boolean checkRevocation) {
        final String trustedCertificates = connection.getTrustedCertificates().orElse(null);
        final TrustManagerFactory factory = newTrustManagerFactory(trustedCertificates, checkRevocation);
        if (connection.isValidateCertificates()) {
            return factory;
        } else {
            return AcceptAnyTrustManager.factory(factory);
        }
    }

    public TrustManagerFactory newInsecureTrustManagerFactory() {
        return InsecureTrustManagerFactory.INSTANCE;
    }

    private TrustManagerFactory createTrustManagerFactory(
            @Nullable final String trustedCertificates,
            final boolean checkForRevocation)
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
            final PKIXBuilderParameters parameters =
                    new PKIXBuilderParameters(DEFAULT_CA_KEYSTORE, new X509CertSelector());
            if (checkForRevocation) {
                parameters.addCertPathChecker(
                        (PKIXCertPathChecker) CertPathBuilder.getInstance(PKIX).getRevocationChecker());
            } else {
                parameters.addCertPathChecker(NoRevocationChecker.getInstance());
            }
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

    /**
     * Handles common ssl exceptions and maps them to Ditto exceptions.
     *
     * @param supplier the supplier that may throw an exception
     * @param <T> the result type
     * @return the result if no exception occurred
     */
    private <T> T handleExceptions(final ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final CertificateException e) {
            final JsonPointer errorLocation = Connection.JsonFields.TRUSTED_CERTIFICATES.getPointer();
            throw exceptionMapper.badFormat(errorLocation, CERTIFICATE_LABEL, "DER")
                    .cause(e)
                    .build();
        } catch (final KeyStoreException e) {
            throw exceptionMapper.fatalError("Engine failed to configure trusted CA certificates")
                    .cause(e)
                    .build();
        } catch (final NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw exceptionMapper.fatalError("Failed to start TLS engine")
                    .cause(e)
                    .build();
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        /**
         * @return the result.
         */
        T get() throws CertificateException, KeyStoreException, NoSuchAlgorithmException,
                InvalidAlgorithmParameterException;
    }
}
