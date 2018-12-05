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
package org.eclipse.ditto.model.connectivity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represent a measurement of a value for multiple time intervals.
 */
public interface Measurement extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the type of the measurement e.g. "inbound" or "outbound"
     */
    String getType();

    /**
     * @return if the measurement represents a successful operation or a failure
     */
    boolean isSuccess();

    /**
     * @return the actual counter values for different intervals
     */
    Map<Duration, Long> getCounts();

    /**
     * @return last instant when the counter was updated
     */
    Instant getLastMessageAt();

    /**
     * Returns all non hidden marked fields of this {@code AddressMetric}.
     *
     * @return a JSON object representation of this Source including only non hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of an {@code Measurement}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the timestamp when the last message was consumed/published.
         */
        public static final JsonFieldDefinition<String> LAST_MESSAGE_AT =
                JsonFactory.newStringFieldDefinition("lastMessageAt", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }
}
