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
package org.eclipse.ditto.model.connectivity;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * TODO doc
 */
public interface SourceMetrics extends Source {

    /**
     *
     * @return
     */
    ConnectionStatus getStatus();

    /**
     *
     * @return
     */
    Optional<String> getStatusDetails();

    /**
     *
     * @return
     */
    long getConsumedMessages();

    /**
     * An enumeration of the known {@code JsonField}s of a {@code SourceMetrics}.
     */
    @Immutable
    final class JsonFields extends Source.JsonFields {

        /**
         * JSON field containing the {@code ConnectionStatus} value.
         */
        public static final JsonFieldDefinition<String> STATUS =
                JsonFactory.newStringFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus} details.
         */
        public static final JsonFieldDefinition<String> STATUS_DETAILS =
                JsonFactory.newStringFieldDefinition("statusDetails", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the amount of consumed messages.
         */
        public static final JsonFieldDefinition<Long> CONSUMED_MESSAGES =
                JsonFactory.newLongFieldDefinition("consumedMessages", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
