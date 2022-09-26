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
package org.eclipse.ditto.policies.model.signals.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.WithPolicyId;

/**
 * Interface for all policy-related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyEvent<T extends PolicyEvent<T>> extends EventsourcedEvent<T>, WithPolicyId,
        SignalWithEntityId<T> {

    /**
     * Type Prefix of Policy events.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Policy resource type.
     */
    String RESOURCE_TYPE = "policy";

    /**
     * PolicyEvents are only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of PolicyEvents.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the identifier of the {@code Policy} related to this event.
     *
     * @return the identifier of the Policy related to this event.
     */
    PolicyId getPolicyEntityId();

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@link PolicyEvent}.
     */
    @Immutable
    final class JsonFields {

        /**
         * Payload JSON field containing the Policy TYPE.
         */
        public static final JsonFieldDefinition<String> POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
