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
package org.eclipse.ditto.base.model.namespaces.signals.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Base interface for all response signals to {@link NamespaceCommand}s.
 *
 * @param <T> the type of the response.
 */
public interface NamespaceCommandResponse<T extends NamespaceCommandResponse<T>>
        extends CommandResponse<T>, WithNamespace {


    /**
     * This class contains definitions for all specific fields of a {@code NamespaceCommandResponse}'s JSON
     * representation.
     */
    @Immutable
    abstract class JsonFields extends CommandResponse.JsonFields {

        /**
         * Namespace for which the command response is emitted.
         */
        public static final JsonFieldDefinition<String> NAMESPACE = JsonFactory.newStringFieldDefinition("namespace",
                FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Resource type checked.
         */
        public static final JsonFieldDefinition<String> RESOURCE_TYPE =
                JsonFactory.newStringFieldDefinition("resourceType", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }

}
