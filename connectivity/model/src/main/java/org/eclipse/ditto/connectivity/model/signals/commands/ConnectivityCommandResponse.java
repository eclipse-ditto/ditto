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
package org.eclipse.ditto.connectivity.model.signals.commands;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Common interface of all possible responses which are related to a given {@link ConnectivityCommand}. Implementations of
 * this interface are required to be immutable.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityCommandResponse<T extends ConnectivityCommandResponse<T>> extends CommandResponse<T> {

    /**
     * Type Prefix of Connectivity command responses.
     */
    String TYPE_PREFIX = "connectivity." + TYPE_QUALIFIER + ":";

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return ConnectivityCommand.RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * This class contains definitions for all specific fields of a {@code ConnectivityCommandResponse}'s JSON representation.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the ConnectivityCommandResponse's connectionId.
         */
        public static final JsonFieldDefinition<String> JSON_CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }
}
