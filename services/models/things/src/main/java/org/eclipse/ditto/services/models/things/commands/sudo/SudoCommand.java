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
package org.eclipse.ditto.services.models.things.commands.sudo;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Aggregates all sudo commands.
 *
 * @param <T> the type of the implementing class.
 */
public interface SudoCommand<T extends SudoCommand> extends Command<T> {

    /**
     * Type Prefix of Sudo commands.
     */
    String TYPE_PREFIX = "things.sudo." + TYPE_QUALIFIER + ":";

    /**
     * Thing sudo resource type.
     */
    String RESOURCE_TYPE = "thing-sudo";

    @Override
    default JsonPointer getResourcePath() {
        // return empty resource path for SudoCommands as this path is currently not needed for SudoCommands:
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Sudo commands do not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    default String getId() {
        return "";
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a Thing command.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the Thing ID.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("payload/thingId", FieldType.REGULAR,
                        JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing a {@link org.eclipse.ditto.json.JsonFieldSelector} to specify the JSON fields included
         * in the things to retrieve.
         */
        public static final JsonFieldDefinition<String> SELECTED_FIELDS =
                JsonFactory.newStringFieldDefinition("payload/selectedFields", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
