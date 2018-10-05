/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.internal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
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

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.credentials.CredentialsVisitor;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;

import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Create SSL context from connection credentials.
 */
public final class SSLContextCreator implements CredentialsVisitor<SSLContext> {

    private static final String TLS12 = "TLSv1.2";
    private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";
    private static final String CERTIFICATE_LABEL = "CERTIFICATE";
    private static final Pattern PRIVATE_KEY_REGEX = Pattern.compile(pemRegex(PRIVATE_KEY_LABEL));
    private static final Pattern IPV6_URI_PATTERN = Pattern.compile("^\\[[A-Fa-f0-9.:\\s]++]$");

    private static final KeyFactory RSA_KEY_FACTORY;
    private static final CertificateFactory X509_CERTIFICATE_FACTORY;

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

    private final DittoHeaders dittoHeaders;
    private final Either<TrustManager, String> trust;

    @Nullable
    private final String hostname;

    private SSLContextCreator(final Either<TrustManager, String> trust,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable String hostname) {
        this.trust = trust;
        this.dittoHeaders = dittoHeaders != null ? dittoHeaders : DittoHeaders.empty();
        this.hostname = stripIpv6Brackets(hostname);
    }

    /**
     * Create an SSL context creator with a preconfigured trust manager.
     *
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @return the SSL context creator.
     */
    public static SSLContextCreator withTrustManager(final TrustManager trustManager,
            @Nullable final DittoHeaders dittoHeaders) {
        return new SSLContextCreator(Left.apply(trustManager), dittoHeaders, null);
    }

    /**
     * Create an SSL context creator that verifies server hostname but accepts all IP addresses.
     *
     * @param connection connection for which to create SSLContext.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @return the SSL context creator.
     */
    public static SSLContextCreator fromConnection(final Connection connection,
            @Nullable final DittoHeaders dittoHeaders) {
        final String trustedCertificates = connection.getTrustedCertificates().orElse(null);
        return of(trustedCertificates, dittoHeaders, connection.getHostname());
    }

    /**
     * Create an SSL context creator that verifies server identity but accepts all IP addresses.
     *
     * @param trustedCertificates certificates to trust; {@code null} to trust the standard certificate authorities.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @param hostnameOrIp hostname to verify in server certificate or a nullable IP address to not verify hostname.
     * @return the SSL context creator.
     */
    public static SSLContextCreator of(@Nullable final String trustedCertificates,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable final String hostnameOrIp) {
        return new SSLContextCreator(Right.apply(trustedCertificates), dittoHeaders, hostnameOrIp);
    }

    @Override
    public SSLContext clientCertificate(final ClientCertificateCredentials credentials) {
        final String clientKeyPem = credentials.getClientKey().orElse(null);
        final String clientCertificatePem = credentials.getClientCertificate().orElse(null);
        final Supplier<KeyManager[]> keyManagerSupplier = newKeyManagerFactory(clientKeyPem, clientCertificatePem);
        final Supplier<TrustManager[]> trustManagerFactory = trust.isRight()
                ? newTrustManagerFactory(trust.right().get())
                : () -> new TrustManager[]{trust.left().get()};
        return newTLSContext(keyManagerSupplier, trustManagerFactory);
    }

    /**
     * Create an SSL context with trusted certificates without client authentication.
     *
     * @return the SSL context
     */
    public SSLContext withoutClientCertificate() {
        return clientCertificate(ClientCertificateCredentials.empty());
    }

    private Supplier<TrustManager[]> newTrustManagerFactory(@Nullable final String trustedCertificates) {
        try {
            final TrustManager[] trustManagers = TrustManagerFactory.newTrustManager(trustedCertificates,
                    this::newKeystore);
            return () -> DittoTrustManager.wrapTrustManagers(trustManagers, hostname);
        } catch (final CertificateException e) {
            final JsonPointer errorLocation = Connection.JsonFields.TRUSTED_CERTIFICATES.getPointer();
            throw badFormat(errorLocation, CERTIFICATE_LABEL, "DER")
                    .cause(e)
                    .build();
        } catch (final KeyStoreException e) {
            throw fatalError("Engine failed to configure trusted CA certificates")
                    .cause(e)
                    .build();
        } catch (final NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw fatalError("Failed to start TLS engine")
                    .cause(e)
                    .build();
        }
    }

    private Supplier<KeyManager[]> newKeyManagerFactory(@Nullable final String clientKeyPem,
            @Nullable final String clientCertificatePem) {

        if (clientKeyPem != null && clientCertificatePem != null) {
            final KeyStore keystore = newKeystore();
            final PrivateKey privateKey = getClientPrivateKey(clientKeyPem);
            final Certificate certificate = getClientCertificate(clientCertificatePem);
            setPrivateKey(keystore, privateKey, certificate);
            setCertificate(keystore, certificate);

            try {
                final KeyManagerFactory keyManagerFactory =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keystore, new char[0]);
                return keyManagerFactory::getKeyManagers;
            } catch (final Exception e) {
                throw fatalError("Engine failed to configure client key and client certificate")
                        .cause(e)
                        .build();
            }
        } else {
            return () -> null;
        }
    }

    private void setPrivateKey(final KeyStore keystore, final PrivateKey privateKey, final Certificate... certs) {
        try {
            keystore.setKeyEntry("key", privateKey, new char[0], certs);
        } catch (final KeyStoreException e) {
            throw fatalError("Engine failed to configure client key")
                    .cause(e)
                    .build();
        }
    }

    private void setCertificate(final KeyStore keystore, final Certificate certificate) {
        try {
            keystore.setCertificateEntry("cert", certificate);
        } catch (final KeyStoreException e) {
            throw fatalError("Engine failed to configure client certificate")
                    .cause(e)
                    .build();
        }
    }

    private PrivateKey getClientPrivateKey(final String privateKeyPem) {
        final Matcher matcher = PRIVATE_KEY_REGEX.matcher(privateKeyPem);
        final Supplier<DittoRuntimeExceptionBuilder> errorSupplier = () -> {
            final JsonPointer errorLocation = Connection.JsonFields.CREDENTIALS.getPointer()
                    .append(ClientCertificateCredentials.JsonFields.CLIENT_KEY.getPointer());
            return badFormat(errorLocation, PRIVATE_KEY_LABEL, "PKCS #8")
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
            throw badFormat(errorLocation, "CERTIFICATE", "DER")
                    .build();
        }
    }

    private SSLContext newTLSContext(final Supplier<KeyManager[]> keyManagerSupplier,
            @Nullable final Supplier<TrustManager[]> trustManagerSupplier) {

        try {
            final SSLContext sslContext = SSLContext.getInstance(TLS12);
            final TrustManager[] trustManagers = trustManagerSupplier != null ? trustManagerSupplier.get() : null;
            sslContext.init(keyManagerSupplier.get(), trustManagers, null);
            return sslContext;
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw fatalError("Cannot start TLS 1.2 engine")
                    .cause(e)
                    .build();
        }
    }

    private KeyStore newKeystore() {
        try {
            return TrustManagerFactory.emptyKeyStore();
        } catch (final Exception e) {
            throw fatalError("Cannot initialize client-side security for connection")
                    .cause(e)
                    .build();
        }
    }

    private DittoRuntimeExceptionBuilder<ConnectionUnavailableException> fatalError(final String whatHappened) {
        return ConnectionUnavailableException.newBuilder("unimportant")
                .message(String.format("Fatal error: %s.", whatHappened))
                .description("Please contact the service team.")
                .dittoHeaders(dittoHeaders);
    }

    private DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badFormat(
            final JsonPointer errorLocation,
            final String label,
            final String binaryFormat) {
        final String message = String.format("%s: bad format. " +
                        "Expect PEM-encoded %s data specified by RFC-7468 starting with '-----BEGIN %s-----'",
                errorLocation.toString(), binaryFormat, label);
        return ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders);
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

    @Nullable
    private static String stripIpv6Brackets(@Nullable final String hostnameOrIp) {
        if (hostnameOrIp != null && IPV6_URI_PATTERN.matcher(hostnameOrIp).matches()) {
            return hostnameOrIp.substring(1, hostnameOrIp.length() - 1);
        } else {
            return hostnameOrIp;
        }
    }
}
