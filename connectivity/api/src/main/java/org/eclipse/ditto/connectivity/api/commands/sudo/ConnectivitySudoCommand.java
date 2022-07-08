/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Aggregates all connectivity sudo commands.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivitySudoCommand<T extends ConnectivitySudoCommand<T>> extends SudoCommand<T> {

    /**
     * Type Prefix of Sudo commands.
     */
    String TYPE_PREFIX = "connectivity." + SUDO_TYPE_QUALIFIER;

    /**
     * Thing sudo resource type.
     */
    String RESOURCE_TYPE = "connectivity-sudo";

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
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a Connectivity sudo command.
     */
    class JsonFields extends Command.JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * JSON field containing the ConnectivitySudoCommand's connectionId.
         */
        public static final JsonFieldDefinition<String> JSON_CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);
    }

}
