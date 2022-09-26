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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.WithPolicyId;

/**
 * Aggregates all possible responses relating to a given {@link PolicyCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyCommandResponse<T extends PolicyCommandResponse<T>> extends CommandResponse<T>,
        WithPolicyId, SignalWithEntityId<T> {

    /**
     * Type Prefix of Policy command responses.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * PolicyCommandResponses are only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of PolicyCommandResponses.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    default String getResourceType() {
        return PolicyCommand.RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    PolicyId getEntityId();

    /**
     * This class contains definitions for all specific fields of a {@code PolicyCommandResponse}'s JSON
     * representation.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the PolicyCommandResponse's policyId.
         */
        public static final JsonFieldDefinition<String> JSON_POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }

}
