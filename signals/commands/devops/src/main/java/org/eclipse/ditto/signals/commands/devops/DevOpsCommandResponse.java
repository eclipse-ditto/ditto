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
package org.eclipse.ditto.signals.commands.devops;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Base interface for all devops command responses.
 *
 * @param <T> the type of the implementing class.
 */
public interface DevOpsCommandResponse<T extends DevOpsCommandResponse> extends CommandResponse<T> {

    /**
     * Type Prefix of DevOps commands.
     */
    String TYPE_PREFIX = "devops.responses:";

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default String getId() {
        return ""; // empty ID for DevOps commands
    }

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
     * @return the instance index of the serviceName from which the DevOpsCommandResponse originated.
     */
    Optional<Integer> getInstance();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a DevOpsCommandResponse.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the serviceName from which the DevOpsCommandResponse originates.
         */
        public static final JsonFieldDefinition<String> JSON_SERVICE_NAME =
                JsonFactory.newStringFieldDefinition("serviceName", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the instance index of the serviceName serviceName from which the DevOpsCommandResponse
         * originates.
         */
        public static final JsonFieldDefinition<Integer> JSON_INSTANCE =
                JsonFactory.newIntFieldDefinition("instance", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }
}
