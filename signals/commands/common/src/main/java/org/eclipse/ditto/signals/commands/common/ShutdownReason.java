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
package org.eclipse.ditto.signals.commands.common;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * <p>
 * The reason which informs why a {@code Shutdown} command was issued.
 * </p>
 * <p>
 * <em>Note: Implementations of this interface are required to be immutable.</em>
 * </p>
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
     * Returns the details of this reason.
     *
     * @return an Optional containing the details of the reason or an empty Optional if the reason does not provide
     * details.
     */
    Optional<String> getDetails();

    /**
     * This class contains definitions for all specific fields of a {@code ShutdownReason}'s JSON representation.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the type of the reason, type: {@code String}, name: {@code "type"}.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the <em>optional</em> details of the reason, type: {@code String},
         * name: {@code "details"}.
         */
        public static final JsonFieldDefinition<String> DETAILS = JsonFactory.newStringFieldDefinition("details",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
