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
package org.eclipse.ditto.services.models.policies.commands.sudo;

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
    String TYPE_PREFIX = "policies.sudo." + TYPE_QUALIFIER + ":";

    /**
     * Policy sudo resource type.
     */
    String RESOURCE_TYPE = "policy-sudo";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

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
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@link SudoCommand}.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the Policy ID.
         */
        public static final JsonFieldDefinition<String> JSON_POLICY_ID =
                JsonFactory.newStringFieldDefinition("payload/policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing a {@link org.eclipse.ditto.json.JsonFieldSelector} to specify the JSON fields included in the things to
         * retrieve.
         */
        public static final JsonFieldDefinition<String> SELECTED_FIELDS =
                JsonFactory.newStringFieldDefinition("payload/selectedFields", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
