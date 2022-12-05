/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.util;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Rule;
import org.junit.Test;


public class EncryptorAesGcmTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void toBase64PreservesSecretKeySpec() throws Exception {
        SecretKey key = EncryptorAesGcm.getAESKey();
        String toBase64Key = EncryptorAesGcm.toBase64String(key.getEncoded());
        SecretKeySpec key2 = new SecretKeySpec(EncryptorAesGcm.fromBase64String(toBase64Key), "AES");
        assertEquals(key, key2);
    }

    @Test
    public void dataIsPreservedOnEncryptWithPrefixIV() throws Exception {
        String input = "This is a plain text which need to be encrypted by Java AES 256 GCM Encryption Algorithm";
        String key = EncryptorAesGcm.toBase64String(EncryptorAesGcm.getAESKey(EncryptorAesGcm.AES_KEY_SIZE).getEncoded());
        String encryptedWithPrefixIV = EncryptorAesGcm.encryptWithPrefixIV(input, key);
        String plainText = EncryptorAesGcm.decryptWithPrefixIV(encryptedWithPrefixIV, key);
        assertEquals(input, plainText);
    }

    @Test
    public void keyLengthVerify() throws NoSuchAlgorithmException {
        String aesKey = EncryptorAesGcm.toBase64String(EncryptorAesGcm.getAESKey().getEncoded());

        SecretKey secretKey = EncryptorAesGcm.toSecretKey(aesKey);
        assertTrue(secretKey.getEncoded().length == 32);
    }
    @Test(expected = DittoConfigError.class)
    public void keyLengthVerifyThrowsOnIllegalLength() throws NoSuchAlgorithmException {
        String aesKey = EncryptorAesGcm.toBase64String(EncryptorAesGcm.getAESKey(128).getEncoded());
        EncryptorAesGcm.toSecretKey(aesKey);
    }

}