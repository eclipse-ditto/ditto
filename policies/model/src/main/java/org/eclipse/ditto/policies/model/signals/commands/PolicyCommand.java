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
package org.eclipse.ditto.policies.model.signals.commands;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.WithPolicyId;

/**
 * Aggregates all {@link Command}s which are related to a {@link org.eclipse.ditto.policies.model.Policy}.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyCommand<T extends PolicyCommand<T>> extends Command<T>, WithEntityType, WithPolicyId,
        SignalWithEntityId<T> {

    /**
     * Type Prefix of Policy commands.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Policy resource type.
     */
    String RESOURCE_TYPE = PolicyConstants.ENTITY_TYPE.toString();

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    PolicyId getEntityId();

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
     * Returns the entity type {@link org.eclipse.ditto.policies.model.PolicyConstants#ENTITY_TYPE}.
     *
     * @return the Policy entity type.
     * @since 1.1.0
     */
    @Override
    default EntityType getEntityType() {
        return PolicyConstants.ENTITY_TYPE;
    }

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
