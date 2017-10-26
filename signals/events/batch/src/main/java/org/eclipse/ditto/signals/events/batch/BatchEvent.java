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
import javax.annotation.concurrent.Immutable;

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
     * Type Prefix of Batch events.
     */
    String TYPE_PREFIX = "batch." + TYPE_QUALIFIER + ":";

    /**
     * Batch resource type.
     */
    String RESOURCE_TYPE = "batch";

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

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
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
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the Batch ID.
         */
        public static final JsonFieldDefinition<String> BATCH_ID =
                JsonFactory.newStringFieldDefinition("batchId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the commands.
         */
        public static final JsonFieldDefinition<JsonArray> COMMANDS =
                JsonFactory.newJsonArrayFieldDefinition("commands", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command.
         */
        public static final JsonFieldDefinition<JsonObject> COMMAND =
                JsonFactory.newJsonObjectFieldDefinition("command", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the commands.
         */
        public static final JsonFieldDefinition<JsonArray> RESPONSES =
                JsonFactory.newJsonArrayFieldDefinition("responses", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command.
         */
        public static final JsonFieldDefinition<JsonObject> RESPONSE =
                JsonFactory.newJsonObjectFieldDefinition("response", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the command header.
         */
        public static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
