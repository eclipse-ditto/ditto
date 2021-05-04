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
package org.eclipse.ditto.base.api.common;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * <p>
 * The reason which informs why a {@code Shutdown} command was issued.
 * </p>
 * <p>
 * <em>Note: Implementations of this interface are required to be immutable.</em>
 * </p>
 *
 */
@Immutable
public interface ShutdownReason extends Jsonifiable.WithPredicate<JsonObject, JsonField> {

    /**
     * Returns the type of this reason.
     *
     * @return the type.
     */
    ShutdownReasonType getType();

    /**
     * Checks whether this shutdown reason is relevant for the given details.
     *
     * @param value the value to check for relevance.
     *
     * @return True if this shut down reason should lead to a shutdown. False if not.
     */
    default boolean isRelevantFor(final String value) {
        return isRelevantFor((Object) value);
    }

    /**
     * Checks whether this shutdown reason is relevant for the given details.
     *
     * @param value the value to check for relevance.
     * @return True if this shut down reason should lead to a shutdown. False if not.
     */
    boolean isRelevantFor(Object value);

    /**
     * This class contains definitions for all specific fields of a {@code ShutdownReason}'s JSON representation.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the type of the reason, type: {@code String}, name: {@code "type"}.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the <em>optional</em> details of the reason, type: {@code String},
         * name: {@code "details"}.
         */
        public static final JsonFieldDefinition<JsonValue> DETAILS =
                JsonFactory.newJsonValueFieldDefinition("details", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
