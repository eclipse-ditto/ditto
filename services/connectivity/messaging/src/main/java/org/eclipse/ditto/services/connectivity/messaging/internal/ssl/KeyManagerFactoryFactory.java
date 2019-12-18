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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.net.ssl.KeyManagerFactory;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;

/**
 * Factory class to create {@link javax.net.ssl.KeyManagerFactory}s.
 */
public final class KeyManagerFactoryFactory implements CredentialsVisitor<KeyManagerFactory> {

    private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";
    private static final Pattern PRIVATE_KEY_REGEX = Pattern.compile(pemRegex(PRIVATE_KEY_LABEL));

    private static final KeyFactory RSA_KEY_FACTORY;
    private static final CertificateFactory X509_CERTIFICATE_FACTORY;

    private final ExceptionMapper exceptionMapper;
    private KeyStoreFactory keyStoreFactory;

    static {
        try {
            RSA_KEY_FACTORY = KeyFactory.getInstance("RSA");
            X509_CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("FATAL: failed to load RSA key or key manager factory", e);
        } catch (final CertificateException e) {
            throw new Error("FATAL: failed to load X.509 certificate factory", e);
        }
    }


    /**
     * @return new instance with empty {@link DittoHeaders}
     */
    public static KeyManagerFactoryFactory getInstance() {
        return new KeyManagerFactoryFactory(new ExceptionMapper(DittoHeaders.empty()));
    }

    /**
     * @param dittoHeaders the ditto headers
     * @return new instance of {@link KeyManagerFactoryFactory}
     */
    public static KeyManagerFactoryFactory getInstance(final DittoHeaders dittoHeaders) {
        return new KeyManagerFactoryFactory(new ExceptionMapper(dittoHeaders));
    }

    /**
     * Instantiates a new {@link KeyManagerFactoryFactory}
     *
     * @param exceptionMapper the {@link ExceptionMapper} to be used
     */
    KeyManagerFactoryFactory(final ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
        this.keyStoreFactory = new KeyStoreFactory(exceptionMapper);
    }

    /**
     * @param clientKeyPem the client key in PEM format
     * @param clientCertificatePem the client certificate in PEM
     * @return the new {@link KeyManagerFactory}
     */
    KeyManagerFactory newKeyManagerFactory(final String clientKeyPem, final String clientCertificatePem) {
        checkNotNull(clientKeyPem, "clientKeyPem");
        checkNotNull(clientCertificatePem, "clientCertificatePem");

        final KeyStore keystore = keyStoreFactory.newKeystore();
        final PrivateKey privateKey = getClientPrivateKey(clientKeyPem);
        final Certificate certificate = getClientCertificate(clientCertificatePem);
        keyStoreFactory.setPrivateKey(keystore, privateKey, certificate);
        keyStoreFactory.setCertificate(keystore, certificate);

        try {
            final KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, new char[0]);
            return keyManagerFactory;
        } catch (final Exception e) {
            throw exceptionMapper.fatalError("Engine failed to configure client key and client certificate")
                    .cause(e)
                    .build();
        }
    }

    private PrivateKey getClientPrivateKey(final String privateKeyPem) {
        final Matcher matcher = PRIVATE_KEY_REGEX.matcher(privateKeyPem);
        final Supplier<DittoRuntimeExceptionBuilder> errorSupplier = () -> {
            final JsonPointer errorLocation = Connection.JsonFields.CREDENTIALS.getPointer()
                    .append(ClientCertificateCredentials.JsonFields.CLIENT_KEY.getPointer());
            return exceptionMapper.badFormat(errorLocation, PRIVATE_KEY_LABEL, "PKCS #8")
                    .description("Please format your client key as PEM-encoded unencrypted PKCS #8.");
        };
        if (!matcher.matches()) {
            throw errorSupplier.get().build();
        } else {
            final String content = matcher.group(1).replaceAll("\\s", "");
            final byte[] bytes = decodeBase64(content);
            final KeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
            try {
                return RSA_KEY_FACTORY.generatePrivate(keySpec);
            } catch (final InvalidKeySpecException e) {
                throw errorSupplier.get().cause(e).build();
            }
        }
    }

    private Certificate getClientCertificate(final String certificatePem) {
        final byte[] asciiBytes = certificatePem.getBytes(StandardCharsets.US_ASCII);
        try {
            return X509_CERTIFICATE_FACTORY.generateCertificate(new ByteArrayInputStream(asciiBytes));
        } catch (final CertificateException e) {
            final JsonPointer errorLocation = Connection.JsonFields.CREDENTIALS.getPointer()
                    .append(ClientCertificateCredentials.JsonFields.CLIENT_CERTIFICATE.getPointer());
            throw exceptionMapper.badFormat(errorLocation, "CERTIFICATE", "DER")
                    .build();
        }
    }


    /**
     * Create PEM regex specified by RFC-7468 section 3 "ABNF".
     *
     * @param label the label.
     * @return regex to capture base64text of textualmsg.
     */
    private static String pemRegex(final String label) {
        final String preeb = String.format("\\s*+-----BEGIN %s-----", label);
        final String posteb = String.format("-----END %s-----\\s*+", label);
        final String contentWhitespaceGroup = "([A-Za-z0-9+/=\\s]*+)";
        return String.format("%s%s%s", preeb, contentWhitespaceGroup, posteb);
    }

    private static byte[] decodeBase64(final String content) {
        return Base64.getDecoder().decode(content.replace("\\s", ""));
    }

    @Override
    public KeyManagerFactory clientCertificate(@Nonnull final ClientCertificateCredentials credentials) {
        final String clientKeyPem = credentials.getClientKey().orElse(null);
        final String clientCertificatePem = credentials.getClientCertificate().orElse(null);

        if (clientKeyPem != null && clientCertificatePem != null) {
            return newKeyManagerFactory(clientKeyPem, clientCertificatePem);
        } else {
            throw exceptionMapper.fatalError("Either client key or certificate were missing").build();
        }
    }

    @Override
    public KeyManagerFactory get(final ClientCertificateCredentials credentials) {
        final String clientKeyPem = credentials.getClientKey().orElse(null);
        final String clientCertificatePem = credentials.getClientCertificate().orElse(null);

        if (credentials.getClientKey().isPresent() && credentials.getClientCertificate().isPresent()) {
            return newKeyManagerFactory(clientKeyPem, clientCertificatePem);
        } else {
            return null;
        }
    }

}
