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
package org.eclipse.ditto.base.api.devops;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Interface for accessing configuration of Loggers.
 */
public interface LoggerConfig extends Jsonifiable.WithPredicate<JsonObject, JsonField> {

    /**
     * Returns the {@code LogLevel} to set.
     *
     * @return the LogLevel to set.
     */
    LogLevel getLevel();

    /**
     * Returns the {@code logger} to change.
     *
     * @return the logger to change or an empty optional.
     */
    Optional<String> getLogger();

    /**
     * Returns all non-hidden marked fields of this object.
     *
     * @return a JSON object representation of this object including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a LoggerConfig.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link LogLevel} to set.
         */
        public static final JsonFieldDefinition<String> LEVEL = JsonFactory.newStringFieldDefinition("level",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the logger to change.
         */
        public static final JsonFieldDefinition<String> LOGGER =
                JsonFactory.newStringFieldDefinition("logger", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
