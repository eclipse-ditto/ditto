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
package org.eclipse.ditto.signals.commands.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Aggregates all {@link Command}s which are related to a {@link org.eclipse.ditto.model.policies.Policy}.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyCommand<T extends PolicyCommand> extends Command<T> {

    /**
     * Type Prefix of Policy commands.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Policy resource type.
     */
    String RESOURCE_TYPE = "policy";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    /**
     * PolicyCommands are only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of PolicyCommands.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * This class contains definitions for all specific fields of a {@code PolicyCommand}'s JSON representation.
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the PolicyCommand's policyId.
         */
        public static final JsonFieldDefinition<String> JSON_POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
