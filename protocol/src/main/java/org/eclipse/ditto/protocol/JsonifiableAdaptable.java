/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * {@link Adaptable} which is also {@link Jsonifiable} (e.g. for usage in plain text protocols like Websockets).
 */
public interface JsonifiableAdaptable extends Adaptable, Jsonifiable<JsonObject> {

    /**
     * Returns this object as {@link JsonObject} with the specified {@code specificHeaders} instead of the ones
     * this Adaptable contains.
     *
     * @param specificHeaders the Headers to use in the created JSON.
     * @return a JSON object representation of this object.
     */
    JsonObject toJson(DittoHeaders specificHeaders);

    @Override
    JsonifiableAdaptable setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Json Fields of the Jsonifiable PlainJsonAdaptable.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the topic.
         */
        public static final JsonFieldDefinition<String> TOPIC = JsonFactory.newStringFieldDefinition("topic");

        /**
         * JSON field containing the headers.
         */
        public static final JsonFieldDefinition<JsonObject> HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers");


        private JsonFields() {
            throw new AssertionError();
        }

    }

}
