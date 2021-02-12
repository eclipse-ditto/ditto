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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;
import org.eclipse.ditto.signals.notifications.policies.SubjectExpiryNotification;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy notifications.
 *
 * @since 2.0.0
 */
final class PolicyNotificationMappingStrategies extends AbstractPolicyMappingStrategies<PolicyNotification<?>> {

    private static final PolicyNotificationMappingStrategies INSTANCE = new PolicyNotificationMappingStrategies();

    private PolicyNotificationMappingStrategies() {
        super(initMappingStrategies());
    }

    /**
     * Get the unique instance of this class.
     *
     * @return the instance.
     */
    public static PolicyNotificationMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<PolicyNotification<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyNotification<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SubjectExpiryNotification.TYPE,
                PolicyNotificationMappingStrategies::toSubjectExpiryNotification);
        return mappingStrategies;
    }

    private static SubjectExpiryNotification toSubjectExpiryNotification(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        final DittoHeaders dittoHeaders = dittoHeadersFrom(adaptable);
        final JsonObject payload = adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseThrow(NoSuchElementException::new);
        final Instant expiry = Instant.parse(payload.getValueOrThrow(SubjectExpiryNotification.JsonFields.EXPIRY));
        final Collection<SubjectId> expiringSubjectIds =
                payload.getValueOrThrow(SubjectExpiryNotification.JsonFields.EXPIRING_SUBJECTS)
                        .stream()
                        .map(JsonValue::asString)
                        .map(SubjectId::newInstance)
                        .collect(Collectors.toList());
        return SubjectExpiryNotification.of(policyId, expiry, expiringSubjectIds, dittoHeaders);
    }

}
