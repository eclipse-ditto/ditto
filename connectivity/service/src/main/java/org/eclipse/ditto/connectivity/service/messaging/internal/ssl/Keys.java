/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Helper class to load certificates, private and public keys.
 */
final class Keys {

    private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";
    private static final Pattern PRIVATE_KEY_REGEX = Pattern.compile(pemRegex(PRIVATE_KEY_LABEL));
    private static final String PUBLIC_KEY_LABEL = "PUBLIC KEY";
    private static final Pattern PUBLIC_KEY_REGEX = Pattern.compile(pemRegex(PUBLIC_KEY_LABEL));

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

    private Keys() {}

    /**
     * Loads a {@link java.security.PrivateKey} from the given string. The private key must be given in PKCS#8 format.
     *
     * @param privateKeyPem private key in PKCS#8 format
     * @param exceptionMapper maps ssl exceptions to ditto exceptions providing context information
     * @return the private key
     */
    static PrivateKey getPrivateKey(final String privateKeyPem, final ExceptionMapper exceptionMapper) {
        final Matcher matcher = PRIVATE_KEY_REGEX.matcher(privateKeyPem);
        final Supplier<DittoRuntimeExceptionBuilder<?>> errorSupplier =
                () -> exceptionMapper.badPrivateKeyFormat(PRIVATE_KEY_LABEL, "PKCS #8")
                        .description("Please format your client key as PEM-encoded unencrypted PKCS #8.");
        try {
            return RSA_KEY_FACTORY.generatePrivate(matchKey(matcher, PKCS8EncodedKeySpec::new, errorSupplier));
        } catch (final InvalidKeySpecException e) {
            throw errorSupplier.get().cause(e).build();
        }
    }

    /**
     * Loads a {@link java.security.PublicKey} from the given string. The public key must be given in PKCS#8 format.
     *
     * @param publicKeyPem public key in PKCS#8 format
     * @param exceptionMapper maps ssl exceptions to ditto exceptions providing context information
     * @return the public key
     */
    static PublicKey getPublicKey(final String publicKeyPem, final ExceptionMapper exceptionMapper) {
        final Matcher matcher = PUBLIC_KEY_REGEX.matcher(publicKeyPem);
        final Supplier<DittoRuntimeExceptionBuilder<?>> errorSupplier =
                () -> exceptionMapper.badPublicKeyFormat(PUBLIC_KEY_LABEL, "PKCS #8")
                        .description("Please format your public key as PEM-encoded unencrypted PKCS #8.");
        try {
            return RSA_KEY_FACTORY.generatePublic(matchKey(matcher, X509EncodedKeySpec::new, errorSupplier));
        } catch (final InvalidKeySpecException e) {
            throw errorSupplier.get().cause(e).build();
        }
    }

    private static <T extends KeySpec> T matchKey(final Matcher matcher, final Function<byte[], T> toKeySpec,
            final Supplier<DittoRuntimeExceptionBuilder<?>> errorSupplier) {
        if (!matcher.matches()) {
            throw errorSupplier.get().build();
        } else {
            final String content = matcher.group(1).replaceAll("\\s", "");
            final byte[] bytes = decodeBase64(content);
            return toKeySpec.apply(bytes);
        }
    }

    /**
     * Loads a {@link java.security.cert.Certificate} from the given string. The private key must be given in PKCS#8 format.
     *
     * @param certificatePem certificate in X.509 format
     * @param exceptionMapper maps ssl exceptions to ditto exceptions providing context information
     * @return the certificate
     */
    static Certificate getCertificate(final String certificatePem, final ExceptionMapper exceptionMapper) {
        final byte[] asciiBytes = certificatePem.getBytes(StandardCharsets.US_ASCII);
        try {
            return X509_CERTIFICATE_FACTORY.generateCertificate(new ByteArrayInputStream(asciiBytes));
        } catch (final CertificateException e) {
            throw exceptionMapper.badCertificateFormat("CERTIFICATE", "DER").build();
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
}
