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
package org.eclipse.ditto.model.connectivity.credentials;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * Credentials of a connection. Credential types are extensible. To add a new credential type, implement this
 * interface and {@link org.eclipse.ditto.model.connectivity.credentials.CredentialsVisitor} and their serialization.
 */
public interface Credentials {

    /**
     * Evaluate this object by a visitor.
     *
     * @param visitor the visitor.
     * @param <T> type of the evaluation result.
     * @return evaluation result.
     */
    <T> T accept(CredentialsVisitor<T> visitor);

    /**
     * Serialize as JSON.
     *
     * @return JSON representation of this object.
     */
    JsonObject toJson();

    /**
     * Register a deserializer for a subclass. Each subclass should call this method at least once before
     * they are considered for deserialization.
     * <p>
     * Warning: Putting deserializer registration in static blocks of subclasses does not guarantee registration
     * because static blocks are executed only if classes are loaded for some other reason.
     * </p>
     *
     * @param type the credential type.
     * @param deserializer deserializer of the subclass.
     */
    static void registerDeserializer(final String type,
            final Function<JsonObject, Credentials> deserializer) {

        JsonFields.DESERIALIZER_MAP.put(type, deserializer);
    }

    /**
     * Deserialize credentials from JSON.
     *
     * @param jsonObject credentials in JSON format.
     * @return deserialized credentials.
     */
    static Credentials fromJson(final JsonObject jsonObject) {
        final String type = jsonObject.getValueOrThrow(JsonFields.TYPE);
        final Function<JsonObject, Credentials> deserializer = JsonFields.DESERIALIZER_MAP.get(type);
        if (deserializer == null) {
            throw JsonParseException.newBuilder()
                    .message(String.format("Unknown credential type <%s>", type))
                    .description("Original JSON: " + jsonObject.toString())
                    .build();
        }
        return deserializer.apply(jsonObject);
    }


    /**
     * JSON fields common to all subclasses.
     */
    abstract class JsonFields {

        private static final ConcurrentMap<String, Function<JsonObject, Credentials>> DESERIALIZER_MAP =
                new ConcurrentHashMap<>();

        /**
         * JSON field definition of the credential type identifier. All subclasses should identify themselves by this
         * field in their serialization.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFieldDefinition.ofString("type");

        static {
            // load subclasses in this package.
            registerDeserializer(ClientCertificateCredentials.TYPE, ClientCertificateCredentials::fromJson);
        }
    }
}
