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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.announcements.SubjectDeletionAnnouncement;

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
        final JsonObject payload = getValueFromPayload(adaptable);
        final Instant expiry = Instant.parse(payload.getValueOrThrow(SubjectDeletionAnnouncement.JsonFields.DELETE_AT));
        final Set<SubjectId> expiringSubjectIds =
                payload.getValueOrThrow(SubjectDeletionAnnouncement.JsonFields.SUBJECT_IDS)
                        .stream()
                        .map(JsonValue::asString)
                        .map(SubjectId::newInstance)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return SubjectDeletionAnnouncement.of(policyId, expiry, expiringSubjectIds, dittoHeaders);
    }

}
