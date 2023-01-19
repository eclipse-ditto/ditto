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
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.model.ConnectionUriInvalidException;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
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
public final class JsonFieldsEncryptor {

    static final String ENCRYPTED_PREFIX = "encrypted_";
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonFieldsEncryptor.class);

    /**
     * Encrypts a json object fields based on a list of pointers with a provided symmetrical key.
     * After encryption values are prefixed with {@value ENCRYPTED_PREFIX} prefix.
     *
     * @param jsonObject the jsonObject whose fields should be encrypted
     * @param pointersPrefix a prefix to be added if needed to all pointers.
     * Use empty string if not needed else can be a string starting or not with "/"
     * @param jsonPointers the pointer to the values to be encrypted
     * @param symmetricKey the symmetrical key to be used for the encryption
     * @return a new encrypted {@link org.eclipse.ditto.json.JsonObject }
     * @throws IllegalStateException if encryption fails.
     * It is not advisable to catch it as in the case of failure it is not expected for the service to continue to work
     * without encryption being properly configured.
     */
    public static JsonObject encrypt(final JsonObject jsonObject, final String pointersPrefix, final List<String> jsonPointers,
            final String symmetricKey) {
        return handle(jsonObject, prefixPointers(pointersPrefix, jsonPointers).map(JsonPointer::of).collect(Collectors.toList()), symmetricKey,
                JsonFieldsEncryptor::encryptValue);
    }

    /**
     * Decrypts a json object fields based on a list of pointers with a provided symmetrical key.
     * Only fields prefixed with {@value ENCRYPTED_PREFIX} prefix will be decrypted even if configured with a pointer.
     *
     * @param jsonObject the jsonObject whose fields should be decrypted
     * @param pointersPrefix a prefix to be added if needed to all pointers.
     * Use empty string if not needed else can be a string starting or not with "/"
     * @param jsonPointers the pointer to the values to be decrypted
     * @param symmetricKey the symmetrical key to be used for the description
     * @return a new decrypted {@link org.eclipse.ditto.json.JsonObject }
     * @throws IllegalStateException if decryption fails.
     * It is not advisable to catch it as in the case of failure it is not expected for the service to continue to work
     * without encryption being properly configured.
     */
    public static JsonObject decrypt(final JsonObject jsonObject, final String pointersPrefix, final List<String> jsonPointers,
            final String symmetricKey) {
        return handle(jsonObject, prefixPointers(pointersPrefix, jsonPointers).map(JsonPointer::of)
                        .collect(Collectors.toList()), symmetricKey, JsonFieldsEncryptor::decryptValue);
    }

    static String replaceUriPassword(final String uriStringRepresentation, final String patchedPassword) {
        final String userInfo = URI.create(uriStringRepresentation).getRawUserInfo();
        final String newUserInfo = userInfo.substring(0, userInfo.indexOf(":") + 1) + patchedPassword;
        final int startOfPwd = uriStringRepresentation.indexOf(userInfo);
        final int endOfPassword = uriStringRepresentation.indexOf("@");
        return uriStringRepresentation.substring(0, startOfPwd) + newUserInfo +
                uriStringRepresentation.substring(endOfPassword);
    }

    public static Stream<String> prefixPointers(final String prefix, final List<String> pointers) {
        final String thePrefix = (prefix.startsWith("/") | prefix.isEmpty()) ? prefix : "/" + prefix;
        return pointers.stream().map(pointer -> thePrefix + pointer);
    }
    private static JsonObject handle(final JsonObject jsonObject, final List<JsonPointer> jsonPointers,
            final String symmetricKey, final BiFunction<String, String, String> encryptionHandler) {
        return jsonPointers.stream()
                .filter(pointer -> jsonObject.getValue(pointer).filter(JsonValue::isString).isPresent())
                .map(pointer -> jsonObject.getValue(pointer).map(jsonValue ->
                        createPatch(pointer, jsonValue.asString(), encryptionHandler, symmetricKey)).orElse(JsonObject.empty()))
                .filter(patch -> !patch.isEmpty())
                .reduce(jsonObject, (updatedJsonObject, patch) -> JsonFactory.mergeJsonValues(patch, updatedJsonObject)
                        .asObject());
    }


    private static JsonObject createPatch(final JsonPointer pointer, final String oldValue,
            final BiFunction<String, String, String> encryptionHandler, final String symmetricKey) {
        try {
            final Optional<String> password = getUriPassword(oldValue);
            return password.map(pwd -> {
                final String patchedPwd = encryptionHandler.apply(pwd, symmetricKey);
                final String patchedUri = replaceUriPassword(oldValue, patchedPwd);
                return JsonFactory.newObject(pointer, JsonValue.of(patchedUri));
            }).orElse(JsonObject.empty());
        } catch (ConnectionUriInvalidException | URISyntaxException e) {
            LOGGER.trace("<{}> value is not a uri, will encrypt whole value.", pointer);
            final String encryptedValue = encryptionHandler.apply(oldValue, symmetricKey);
            return JsonFactory.newObject(pointer, JsonValue.of(encryptedValue));
        } catch (Exception ise) {
            LOGGER.warn("{} of connection value at <{}> failed", ise.getMessage(), pointer, ise);
            throw new IllegalStateException(
                    String.format("%s of connection value at <%s> failed", ise.getMessage(), pointer), ise);
        }
    }

    private static String decryptValue(final String value, final String symmetricKey) {
        if (value.startsWith(ENCRYPTED_PREFIX)) {
            final String striped = value.replace(ENCRYPTED_PREFIX, "");
            try {
                return EncryptorAesGcm.decryptWithPrefixIV(striped, symmetricKey);
            } catch (Exception e) {
                throw new IllegalStateException("Decryption", e);
            }
        }
        return value;
    }

    private static String encryptValue(final String value, final String symmetricKey) {
        try {
            return ENCRYPTED_PREFIX + EncryptorAesGcm.encryptWithPrefixIV(value, symmetricKey);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption", e);
        }
    }

    private static Optional<String> getUriPassword(final String uriStringRepresentation) throws URISyntaxException {
        final URI uri = new URI(uriStringRepresentation);
        final String protocol = uri.getScheme();
        if (protocol == null) {
            throw ConnectionUriInvalidException.newBuilder(uriStringRepresentation)
                    .message("Not a valid connection URI")
                    .build();
        }
        final String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            return Optional.empty();
        }
        final String[] userPass = userInfo.split(":", 2);
        return userPass.length == 2 ? Optional.of(userPass[1]) : Optional.empty();
    }
}
