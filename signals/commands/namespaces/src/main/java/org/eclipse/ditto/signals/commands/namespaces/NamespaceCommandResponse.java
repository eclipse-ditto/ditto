/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.namespaces;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Base interface for all response signals to {@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommand}s.
 *
 * @param <T> the type of the response.
 */
public interface NamespaceCommandResponse<T extends NamespaceCommandResponse>
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
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * Resource type checked.
         */
        public static final JsonFieldDefinition<String> RESOURCE_TYPE =
                JsonFactory.newStringFieldDefinition("resourceType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
