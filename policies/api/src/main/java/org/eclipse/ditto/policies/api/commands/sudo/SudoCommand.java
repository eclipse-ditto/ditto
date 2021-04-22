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
package org.eclipse.ditto.policies.api.commands.sudo;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;

/**
 * Aggregates all sudo commands.
 *
 * @param <T> the type of the implementing class.
 */
public interface SudoCommand<T extends SudoCommand<T>> extends Command<T> {

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
                JsonFactory.newStringFieldDefinition("payload/selectedFields", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
