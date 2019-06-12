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
package org.eclipse.ditto.signals.commands.cleanup;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

public interface CleanupCommand<T extends CleanupCommand> extends Command<T> {

    /**
     * Type Prefix of Thing commands.
     */
    String TYPE_PREFIX = "cleanup." + TYPE_QUALIFIER + ":";

    /**
     * Thing resource type.
     */
    String RESOURCE_TYPE = "cleanup";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    default String getId() {
        return getEntityId();
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

    String getEntityId();

    /**
     * This class contains definitions for all specific fields of a {@code CleanupCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the CleanupCommand's entityId.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }
}
