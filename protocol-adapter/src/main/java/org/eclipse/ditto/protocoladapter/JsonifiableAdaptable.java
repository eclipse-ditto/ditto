/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;

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
