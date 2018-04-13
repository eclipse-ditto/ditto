/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A {@link Connection} target contains one address to publish to and several topics of Ditto signals for which to
 * subscribe in the Ditto cluster.
 */
public interface Target extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the address for the configured type of signals of this target
     */
    String getAddress();

    /**
     * @return set of topics that should be published via this target
     */
    Set<String> getTopics();

    /**
     * Returns all non hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Target including only non hidden marked fields
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Target} configuration.
     */
    @Immutable
    final class JsonFields {


        /**
         * JSON field containing the {@code JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} address.
         */
        public static final JsonFieldDefinition<String> ADDRESS =
                JsonFactory.newStringFieldDefinition("address", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} topics.
         */
        public static final JsonFieldDefinition<JsonArray> TOPICS =
                JsonFactory.newJsonArrayFieldDefinition("topics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);


        JsonFields() {
            throw new AssertionError();
        }
    }
}
