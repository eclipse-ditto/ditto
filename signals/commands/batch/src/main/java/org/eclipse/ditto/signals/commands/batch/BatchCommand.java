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
package org.eclipse.ditto.signals.commands.batch;


import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Aggregates all {@link Command}s which are related to a {@code Batch}.
 *
 * @param <T> the type of the implementing class.
 */
public interface BatchCommand<T extends BatchCommand> extends Command<T> {

    /**
     * Type Prefix of Batch commands.
     */
    String TYPE_PREFIX = "batch." + TYPE_QUALIFIER + ":";

    /**
     * Batch resource type.
     */
    String RESOURCE_TYPE = "batch";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
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
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a BatchCommand.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the batch ID.
         */
        public static final JsonFieldDefinition<String> BATCH_ID =
                JsonFactory.newStringFieldDefinition("batchId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
