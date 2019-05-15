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
package org.eclipse.ditto.signals.commands.batch;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Aggregates all possible responses relating to a given {@link BatchCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface BatchCommandResponse<T extends BatchCommandResponse> extends CommandResponse<T>, WithId {

    /**
     * Type Prefix of Batch command responses.
     */
    String TYPE_PREFIX = "batch." + TYPE_QUALIFIER + ":";

    @Override
    default String getId() {
        return getBatchId();
    }

    @Override
    default String getResourceType() {
        return BatchCommand.RESOURCE_TYPE;
    }

    /**
     * Returns the identifier of the batch.
     *
     * @return the identifier of the batch.
     */
    String getBatchId();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a BatchCommandResponse.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the batch ID.
         */
        public static final JsonFieldDefinition<String> BATCH_ID =
                JsonFactory.newStringFieldDefinition("batchId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
