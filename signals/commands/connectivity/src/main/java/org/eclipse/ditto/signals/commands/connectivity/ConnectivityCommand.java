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
package org.eclipse.ditto.signals.commands.connectivity;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityConstants;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Base interface for all commands which are understood by the Connectivity service. Implementations of this interface
 * are required to be immutable.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityCommand<T extends ConnectivityCommand> extends Command<T> {

    /**
     * Type Prefix of Connectivity commands.
     */
    String TYPE_PREFIX = "connectivity." + TYPE_QUALIFIER + ":";

    /**
     * Connectivity resource type.
     */
    String RESOURCE_TYPE = ConnectivityConstants.ENTITY_TYPE.toString();

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     * @deprecated entity IDs are now typed. Use {@link #getConnectionEntityId()} instead.
     */
    @Deprecated
    default String getConnectionId() {
        return String.valueOf(getConnectionEntityId());
    }

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     */
    ConnectionId getConnectionEntityId();

    @Override
    default ConnectionId getEntityId() {
        return getConnectionEntityId();
    }

    @Override
    default JsonPointer getResourcePath() {
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
     * This class contains definitions for all specific fields of a {@code ConnectivityCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the ConnectivityCommand's connectionId.
         */
        public static final JsonFieldDefinition<String> JSON_CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
