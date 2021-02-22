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
import org.eclipse.ditto.signals.announcements.policies.PolicyAnnouncement;
import org.eclipse.ditto.signals.announcements.policies.SubjectDeletionAnnouncement;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy announcements.
 *
 * @since 2.0.0
 */
final class PolicyAnnouncementMappingStrategies extends AbstractPolicyMappingStrategies<PolicyAnnouncement<?>> {

    private static final PolicyAnnouncementMappingStrategies INSTANCE = new PolicyAnnouncementMappingStrategies();

    private PolicyAnnouncementMappingStrategies() {
        super(initMappingStrategies());
    }

    /**
     * Get the unique instance of this class.
     *
     * @return the instance.
     */
    public static PolicyAnnouncementMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<PolicyAnnouncement<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyAnnouncement<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SubjectDeletionAnnouncement.TYPE,
                PolicyAnnouncementMappingStrategies::toSubjectDeletionAnnouncement);
        return mappingStrategies;
    }

    private static SubjectDeletionAnnouncement toSubjectDeletionAnnouncement(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        final DittoHeaders dittoHeaders = dittoHeadersFrom(adaptable);
        final JsonObject payload = adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseThrow(NoSuchElementException::new);
        final Instant expiry = Instant.parse(payload.getValueOrThrow(SubjectDeletionAnnouncement.JsonFields.DELETED_AT));
        final Collection<SubjectId> expiringSubjectIds =
                payload.getValueOrThrow(SubjectDeletionAnnouncement.JsonFields.SUBJECT_IDS)
                        .stream()
                        .map(JsonValue::asString)
                        .map(SubjectId::newInstance)
                        .collect(Collectors.toList());
        return SubjectDeletionAnnouncement.of(policyId, expiry, expiringSubjectIds, dittoHeaders);
    }

}
