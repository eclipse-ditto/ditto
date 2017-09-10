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
package org.eclipse.ditto.signals.events.batch;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Interface for all Batch-related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface BatchEvent<T extends BatchEvent> extends Event<T> {

    /**
     * Type Prefix of Topology events.
     */
    String TYPE_PREFIX = "batch." + TYPE_QUALIFIER + ":";

    @Nonnull
    @Override
    default String getManifest() {
        return getType();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default long getRevision() {
        throw new UnsupportedOperationException("This Event does not support a revision!");
    }

    @Override
    default T setRevision(final long revision) {
        throw new UnsupportedOperationException("This Event does not support a revision!");
    }

    @Override
    default String getId() {
        return getBatchId();
    }

    /**
     * Returns the identifier of the batch.
     *
     * @return the identifier of the batch.
     */
    String getBatchId();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    final class JsonFields {

        /**
         * JSON field containing the Batch ID.
         */
        public static final JsonFieldDefinition BATCH_ID =
                JsonFactory.newFieldDefinition("batchId", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the commands.
         */
        public static final JsonFieldDefinition COMMANDS =
                JsonFactory.newFieldDefinition("commands", JsonArray.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command.
         */
        public static final JsonFieldDefinition COMMAND =
                JsonFactory.newFieldDefinition("command", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the commands.
         */
        public static final JsonFieldDefinition RESPONSES =
                JsonFactory.newFieldDefinition("responses", JsonArray.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command.
         */
        public static final JsonFieldDefinition RESPONSE =
                JsonFactory.newFieldDefinition("response", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command header.
         */
        public static final JsonFieldDefinition DITTO_HEADERS =
                JsonFactory.newFieldDefinition("dittoHeaders", JsonObject.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
