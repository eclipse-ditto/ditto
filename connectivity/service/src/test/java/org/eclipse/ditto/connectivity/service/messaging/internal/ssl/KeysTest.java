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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

/**
 * Tests {@link Keys} class.
 */
public class KeysTest {

    private static final ExceptionMapper EXCEPTION_MAPPER = new ExceptionMapper(DittoHeaders.empty(),
            JsonPointer.empty(), JsonPointer.empty(), JsonPointer.empty());

    @Test
    public void loadPrivateKey() {
        Keys.getPrivateKey(TestConstants.Certificates.SERVER_KEY, EXCEPTION_MAPPER);
    }

    @Test
    public void loadPublicKey() {
        Keys.getPublicKey(TestConstants.Certificates.SERVER_PUB, EXCEPTION_MAPPER);
    }

    @Test
    public void loadCertificate() {
        Keys.getCertificateChain(TestConstants.Certificates.SERVER_CRT, EXCEPTION_MAPPER);
    }

    @Test
    public void loadEcPrivateKey() throws NoSuchAlgorithmException {
        assertLoadsEcPrivateKey(256);
    }

    @Test
    public void loadEcPrivateKeyOfP384Curve() throws NoSuchAlgorithmException {
        assertLoadsEcPrivateKey(384);
    }

    @Test
    public void loadEcPublicKey() throws NoSuchAlgorithmException {
        final ECPublicKey expected = (ECPublicKey) generateEcKeyPair(256).getPublic();

        final PublicKey publicKey = Keys.getPublicKey(toPem("PUBLIC KEY", expected.getEncoded()), EXCEPTION_MAPPER);

        assertThat(publicKey).isInstanceOf(ECPublicKey.class);
        assertThat(((ECPublicKey) publicKey).getW()).isEqualTo(expected.getW());
    }

    @Test
    public void rejectMalformedPrivateKey() {
        final String pem = toPem("PRIVATE KEY", new byte[]{1, 2, 3, 4});

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> Keys.getPrivateKey(pem, EXCEPTION_MAPPER));
    }

    @Test
    public void rejectMalformedPublicKey() {
        final String pem = toPem("PUBLIC KEY", new byte[]{1, 2, 3, 4});

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> Keys.getPublicKey(pem, EXCEPTION_MAPPER));
    }

    private static void assertLoadsEcPrivateKey(final int keySize) throws NoSuchAlgorithmException {
        final ECPrivateKey expected = (ECPrivateKey) generateEcKeyPair(keySize).getPrivate();

        final PrivateKey privateKey = Keys.getPrivateKey(toPem("PRIVATE KEY", expected.getEncoded()), EXCEPTION_MAPPER);

        assertThat(privateKey).isInstanceOf(ECPrivateKey.class);
        assertThat(((ECPrivateKey) privateKey).getS()).isEqualTo(expected.getS());
    }

    private static KeyPair generateEcKeyPair(final int keySize) throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

    private static String toPem(final String label, final byte[] der) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder().encodeToString(der) +
                "\n-----END " + label + "-----\n";
    }
}
