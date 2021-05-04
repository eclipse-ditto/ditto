/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.announcements;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.base.model.signals.announcements.Announcement;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;

/**
 * Announcements for policies.
 *
 * @since 2.0.0
 */
public interface PolicyAnnouncement<T extends PolicyAnnouncement<T>> extends Announcement<T>, SignalWithEntityId<T> {

    /**
     * Type prefix of policy announcements.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Policy resource type.
     */
    String RESOURCE_TYPE = PolicyConstants.ENTITY_TYPE.toString();



    @Override
    PolicyId getEntityId();

    /**
     * Definition of fields of the JSON representation of a {@link PolicyAnnouncement}.
     */
    final class JsonFields {

        /**
         * Json field for the policy ID.
         */
        public static final JsonFieldDefinition<String> JSON_POLICY_ID =
                JsonFactory.newStringFieldDefinition("policyId", JsonSchemaVersion.V_2, FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
