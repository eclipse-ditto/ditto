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

package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionUriInvalidException;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Handles encryption of fields of a given json object.
 *  Json values which are considered a connection URI are treated differently.
 *  Only the password of the user info part if present will be encrypted.
 *  <p>
 *  For example:
 *  <p>
 *  "amqps://user:password@amqpbroker.eclipseprojects.io:5671" <br/>
 *  "amqps://user:encrypted_NOHOcSMyBvwTQWR4kCTd642m8@hono.eclipseprojects.io:5671"
 */
public class JsonFieldsEncryptor {

    private static final String ENCRYPTED_PREFIX = "encrypted_";
    public static final Logger LOGGER = LoggerFactory.getLogger(JsonFieldsEncryptor.class);

    /**
     * Encrypts a json object fields based on a list of pointers with a provided symmetrical key.
     * After encryption values are prefixed with {@value ENCRYPTED_PREFIX} prefix.
     *
     * @param jsonObject the jsonObject whose fields should be encrypted
     * @param jsonPointers the pointer to the values to be encrypted
     * @param symmetricKey the symmetrical key to be used for the encryption
     * @return a new encrypted {@link org.eclipse.ditto.json.JsonObject }
     */
    public static JsonObject encrypt(final JsonObject jsonObject, final Collection<String> jsonPointers,
            final String symmetricKey) {
        return handle(jsonObject, jsonPointers.stream().map(JsonPointer::of).collect(Collectors.toList()), symmetricKey,
                JsonFieldsEncryptor::encryptValue);
    }

    /**
     * Decrypts a json object fields based on a list of pointers with a provided symmetrical key.
     * Only fields prefixed with {@value ENCRYPTED_PREFIX} prefix will be decrypted even if configured with a pointer.
     *
     * @param jsonObject the jsonObject whose fields should be decrypted
     * @param jsonPointers the pointer to the values to be decrypted
     * @param symmetricKey the symmetrical key to be used for the description
     * @return a new decrypted {@link org.eclipse.ditto.json.JsonObject }
     */
    public static JsonObject decrypt(final JsonObject jsonObject, final Collection<String> jsonPointers,
            final String symmetricKey) {
        return handle(jsonObject, jsonPointers.stream().map(JsonPointer::of).collect(Collectors.toList()), symmetricKey,
                JsonFieldsEncryptor::decryptValue);
    }

    private static JsonObject handle(final JsonObject jsonObject, final List<JsonPointer> jsonPointers,
            final String symmetricKey, final BiFunction<String, String, String> encryptionHandler) {
        return jsonPointers.stream()
                .filter(pointer -> jsonObject.getValue(pointer).isPresent())
                .map(pointer -> createPatch(pointer, jsonObject, encryptionHandler, symmetricKey))
                .filter(patch -> !patch.isEmpty())
                .reduce(jsonObject, (updatedJsonObject, patch) -> JsonFactory.mergeJsonValues(patch, updatedJsonObject)
                        .asObject());
    }


    private static JsonObject createPatch(final JsonPointer pointer, final JsonObject jsonObject,
            final BiFunction<String, String, String> encryptionHandler, final String symmetricKey) {
        final JsonValue oldValue = jsonObject.getValue(pointer).get(); // pointers to non-existing values are filtered out
        try {

            final String password = getUriPassword(oldValue.asString());
            if (password == null) {
                return JsonObject.empty();
            }
            final String encryptedPwd = encryptionHandler.apply(password, symmetricKey);
            final String encryptedValue = oldValue.asString().replace(password, encryptedPwd);
            return JsonFactory.newObject(pointer, JsonValue.of(encryptedValue));

        } catch (ConnectionUriInvalidException | URISyntaxException e) {
            final String encryptedValue = encryptionHandler.apply(oldValue.asString(), symmetricKey);
            return JsonFactory.newObject(pointer, JsonValue.of(encryptedValue));
        } catch (RuntimeException re) {
            LOGGER.warn("{} of connection value at <{}> failed", re.getMessage(), pointer, re);
            return JsonObject.empty();
        }
    }

    private static String decryptValue(final String value, final String symmetricKey) {
        if (value.startsWith(ENCRYPTED_PREFIX)) {
            final String striped = value.replace(ENCRYPTED_PREFIX, "");
            try {
                return EncryptorAesGcm.decryptWithPrefixIV(striped, symmetricKey);
            } catch (Exception e) {
                throw new RuntimeException("Decryption", e);
            }
        }
        return value;
    }

    private static String encryptValue(final String value, final String symmetricKey) {
        try {
            return ENCRYPTED_PREFIX + EncryptorAesGcm.encryptWithPrefixIV(value, symmetricKey);
        } catch (Exception e) {
            throw new RuntimeException("Encryption", e);
        }
    }

    @Nullable
    private static String getUriPassword(final String value) throws URISyntaxException {
        final URI uri = new URI(value);
        final String protocol = uri.getScheme();
        if (protocol == null) {
            throw ConnectionUriInvalidException.newBuilder(value)
                    .message("Not a valid connection URI")
                    .build();
        }
        final String userInfo = uri.getUserInfo();
        if (userInfo == null) {
            return null;
        }
        final String[] userPass = userInfo.split(":", 2);
        return userPass.length == 2 ? userPass[1] : null;
    }
}
