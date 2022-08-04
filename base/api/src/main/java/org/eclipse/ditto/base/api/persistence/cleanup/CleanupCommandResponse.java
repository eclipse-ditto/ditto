/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.persistence.cleanup;

import org.eclipse.ditto.base.api.commands.sudo.SudoQueryCommandResponse;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Aggregates all possible responses relating to a given {@link CleanupCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface CleanupCommandResponse<T extends CleanupCommandResponse<T>> extends SudoQueryCommandResponse<T>,
        SignalWithEntityId<T> {

    /**
     * Type Prefix of Cleanup command responses.
     */
    String TYPE_PREFIX = "cleanup." + SUDO_TYPE_QUALIFIER;

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "cleanup";

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    /**
     * @return the entity ID for which snapshots and journal entries for in the database were cleaned up.
     */
    @Override
    EntityId getEntityId();

    /**
     * This class contains definitions for all specific fields of a {@code CleanupCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the CleanupCommand's entityId.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the CleanupCommand's entityId.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }
}
