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
package org.eclipse.ditto.policies.api.commands.sudo;

import org.eclipse.ditto.base.api.commands.sudo.SudoQueryCommandResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonValue;

/**
 * Aggregates all PolicySudoCommand Responses.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicySudoQueryCommandResponse<T extends PolicySudoQueryCommandResponse<T>> extends
        SudoQueryCommandResponse<T> {

    /**
     * Type Prefix of Policy sudo command responses.
     */
    String TYPE_PREFIX = "policies." + SUDO_TYPE_QUALIFIER ;

    @Override
    default String getResourceType() {
        return PolicySudoCommand.RESOURCE_TYPE;
    }

    @Override
    T setEntity(JsonValue entity);

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@link PolicySudoQueryCommandResponse}.
     */
    class JsonFields extends CommandResponse.JsonFields {

        /**
         * JSON field containing the Policy ID.
         */
        public static final JsonFieldDefinition<String> JSON_POLICY_ID =
                JsonFactory.newStringFieldDefinition("payload/policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
