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
package org.eclipse.ditto.signals.commands.connectivity;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
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
    String RESOURCE_TYPE = "connectivity";

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     */
    String getConnectionId();

    @Override
    default String getId() {
        return getConnectionId();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonFactory.emptyPointer();
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
