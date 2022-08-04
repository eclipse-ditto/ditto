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

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Aggregates all {@link org.eclipse.ditto.base.model.signals.commands.Command}s which are related to cleaning up (e.g. journal entries) in the database.
 *
 * @param <T> the type of the implementing class.
 */
public interface CleanupCommand<T extends CleanupCommand<T>> extends SudoCommand<T>, SignalWithEntityId<T> {

    /**
     * Type Prefix of Thing commands.
     */
    String TYPE_PREFIX = "cleanup." + SUDO_TYPE_QUALIFIER;

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "cleanup";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default Category getCategory() {
        return Category.MODIFY;
    }

    /**
     * @return the entity ID to cleanup snapshots and journal entries for in the database.
     */
    @Override
    EntityId getEntityId();

    /**
     * This class contains definitions for all specific fields of a {@code CleanupCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the CleanupCommand's entity type.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR, JsonSchemaVersion.V_2);


        /**
         * JSON field containing the CleanupCommand's entityId.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }
}
