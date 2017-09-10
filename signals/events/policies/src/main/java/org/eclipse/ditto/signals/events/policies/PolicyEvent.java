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
package org.eclipse.ditto.signals.events.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Interface for all policy-related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyEvent<T extends PolicyEvent> extends Event<T> {

    /**
     * Type Prefix of Policy events.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Type Prefix of external Policy events.
     *
     */
    String TYPE_PREFIX_EXTERNAL = "policies." + TYPE_QUALIFIER + "." + EXTERNAL + ":";

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
    String getPolicyId();

    @Override
    default String getId() {
        return getPolicyId();
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@link PolicyEvent}.
     *
     */
    final class JsonFields {

        /**
         * Payload JSON field containing the Policy TYPE.
         */
        public static final JsonFieldDefinition POLICY_ID =
                JsonFactory.newFieldDefinition("policyId", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
