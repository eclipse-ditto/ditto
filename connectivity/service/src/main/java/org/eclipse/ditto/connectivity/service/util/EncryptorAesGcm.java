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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;


/**
 * Encrypts strings with AES GCM Encryption Algorithm
 */
public class EncryptorAesGcm {

    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH = 16;

    /**
     * Encrypts given string using the AES/GCM/NoPadding transformation using 256bit symmetrical key
     * The encrypted result has the Initialisation Vector (IV) used for encryption prefixed.
     *
     * @param forEncrypt the string to encrypt
     * @param key the base64 urlEncoded 256 bits AES secret key used for encryption
     * @return the base64 urlEncoded encrypted string
     */
    public static String encryptWithPrefixIV(final String forEncrypt, final String key)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        SecretKey secretKey = toSecretKey(key);
        final byte[] iv = getRandomNonceIV();
        final byte[] cipherText = encrypt(forEncrypt.getBytes(UTF_8), secretKey, iv);
        final byte[] cipherTextWithIv = ByteBuffer.allocate(iv.length + cipherText.length)
                .put(iv)
                .put(cipherText)
                .array();
        return toBase64String(cipherTextWithIv);
    }

    /**
     * Decrypts given string using the AES/GCM/NoPadding transformation using 256bit symmetrical key
     *
     * @param forDecrypt the base64 erlEncoded encrypted string
     * @param key the base64 urlEncoded 256 bits AES secret key used for decryption
     * @return the decrypted string
     */
    public static String decryptWithPrefixIV(String forDecrypt, String key)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        SecretKey secretKey = toSecretKey(key);
        ByteBuffer bb = ByteBuffer.wrap(fromBase64String(forDecrypt));
        byte[] iv = new byte[GCM_IV_LENGTH];
        bb.get(iv, 0, iv.length);
        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);
        return decrypt(cipherText, secretKey, iv);

    }


    /**
     * Generates 256bits AES symmetrical key
     *
     * @return the key
     * @throws NoSuchAlgorithmException if no Provider supports a KeyGeneratorSpi implementation for the AES algorithm
     */
    public static SecretKey getAESKey() throws NoSuchAlgorithmException {
        return getAESKey(AES_KEY_SIZE);
    }

    /**
     * Returns a 96-bit (12 bytes) Initialisation Vector (IV) needed by AES-GCM.
     * {@link java.util.concurrent.ThreadLocalRandom} is used to fill the IV instead of
     * {@link java.security.SecureRandom } as it can be very slow on linux systems and sometimes even block and lock
     * in multithreaded cases.
     *
     * @return returns the 96-bit (12 bytes) IV byte array
     */
    static byte[] getRandomNonceIV() {
        byte[] nonce = new byte[GCM_IV_LENGTH];
        ThreadLocalRandom.current().nextBytes(nonce);
        return nonce;
    }

    static SecretKey getAESKey(int size) throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(size);
        return keyGen.generateKey();
    }

    static byte[] fromBase64String(final String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    static String toBase64String(final byte[] value) {
        return Base64.getUrlEncoder().encodeToString(value);
    }

    static SecretKey toSecretKey(String base64Key) {
        SecretKey secretKey = new SecretKeySpec(fromBase64String(base64Key), "AES");
        int length = secretKey.getEncoded().length;
        if (length != 32) {
            throw new DittoConfigError(String.format("%d bits symmetrical key required. Provided is %d bits key.", AES_KEY_SIZE, length * 8));
        }
        return secretKey;
    }

    private static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] IV)
            throws DittoConfigError, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

        return cipher.doFinal(plaintext);
    }

    private static String decrypt(byte[] cipherText, SecretKey key, byte[] IV)
            throws DittoConfigError, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, IV);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        byte[] decryptedText = cipher.doFinal(cipherText);

        return new String(decryptedText);
    }
}