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
package org.eclipse.ditto.signals.notifications.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyConstants;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.signals.notifications.base.Notification;

/**
 * Notifications from policies.
 * @since 2.0.0
 */
public interface PolicyNotification<T extends PolicyNotification<T>> extends Notification<T> {

    /**
     * Type prefix of policy notifications.
     */
    String TYPE_PREFIX = "policies." + TYPE_QUALIFIER + ":";

    /**
     * Policy resource type.
     */
    String RESOURCE_TYPE = PolicyConstants.ENTITY_TYPE.toString();

    /**
     * Json field for the policy ID.
     */
    JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFactory.newStringFieldDefinition("policyId", JsonSchemaVersion.V_2, FieldType.REGULAR);


    @Override
    PolicyId getEntityId();
}
