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
package org.eclipse.ditto.base.api.devops.signals.commands;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Base interface for all devops command responses.
 *
 * @param <T> the type of the implementing class.
 */
public interface DevOpsCommandResponse<T extends DevOpsCommandResponse<T>> extends CommandResponse<T> {

    /**
     * Type Prefix of DevOps commands.
     */
    String TYPE_PREFIX = "devops.responses:";

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default String getResourceType() {
        return DevOpsCommand.RESOURCE_TYPE;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty(); // empty resource path for DevOps commands
    }

    /**
     * @return the service name from which the DevOpsCommandResponse originated.
     */
    Optional<String> getServiceName();

    /**
     * @return the instance identifier of the serviceName from which the DevOpsCommandResponse originated.
     */
    Optional<String> getInstance();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a DevOpsCommandResponse.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the serviceName from which the DevOpsCommandResponse originates.
         */
        public static final JsonFieldDefinition<String> JSON_SERVICE_NAME =
                JsonFactory.newStringFieldDefinition("serviceName", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the instance identifier of the serviceName serviceName from which the DevOpsCommandResponse
         * originates.
         */
        public static final JsonFieldDefinition<String> JSON_INSTANCE =
                JsonFactory.newStringFieldDefinition("instance", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }
}
