/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntriesModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.eclipse.ditto.policies.model.signals.events.ResourceCreated;
import org.eclipse.ditto.policies.model.signals.events.ResourceDeleted;
import org.eclipse.ditto.policies.model.signals.events.ResourceModified;
import org.eclipse.ditto.policies.model.signals.events.ResourcesModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;
import org.eclipse.ditto.policies.model.signals.events.SubjectModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsDeletedPartially;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy events.
 */
final class PolicyEventMappingStrategies extends AbstractPolicyMappingStrategies<PolicyEvent<?>> {

    private static final PolicyEventMappingStrategies INSTANCE = new PolicyEventMappingStrategies();

    private PolicyEventMappingStrategies() {
        super(initMappingStrategies());
    }

    static PolicyEventMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<PolicyEvent<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies = new HashMap<>();
        addTopLevelEvents(mappingStrategies);
        addPolicyEntriesEvents(mappingStrategies);
        addPolicyEntryEvents(mappingStrategies);
        addResourcesEvents(mappingStrategies);
        addResourceEvents(mappingStrategies);
        addSubjectsEvents(mappingStrategies);
        addSubjectEvents(mappingStrategies);
        return mappingStrategies;
    }

    private static void addTopLevelEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(PolicyCreated.TYPE,
                adaptable -> PolicyCreated.of(policyFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(PolicyModified.TYPE,
                adaptable -> PolicyModified.of(policyFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(PolicyDeleted.TYPE,
                adaptable -> PolicyDeleted.of(policyIdFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addPolicyEntriesEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(PolicyEntriesModified.TYPE,
                adaptable -> PolicyEntriesModified.of(policyIdFrom(adaptable),
                        policyEntriesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addPolicyEntryEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(PolicyEntryCreated.TYPE,
                adaptable -> PolicyEntryCreated.of(policyIdFrom(adaptable),
                        policyEntryFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(PolicyEntryModified.TYPE,
                adaptable -> PolicyEntryModified.of(policyIdFrom(adaptable),
                        policyEntryFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(PolicyEntryDeleted.TYPE,
                adaptable -> PolicyEntryDeleted.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addResourcesEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(ResourcesModified.TYPE,
                adaptable -> ResourcesModified.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        resourcesFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addResourceEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(ResourceCreated.TYPE,
                adaptable -> ResourceCreated.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        resourceFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ResourceModified.TYPE,
                adaptable -> ResourceModified.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        resourceFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(ResourceDeleted.TYPE,
                adaptable -> ResourceDeleted.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addSubjectsEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(SubjectsModified.TYPE,
                adaptable -> SubjectsModified.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        subjectsFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(SubjectsModifiedPartially.TYPE,
                adaptable -> SubjectsModifiedPartially.of(policyIdFrom(adaptable),
                        activatedSubjectsFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(SubjectsDeletedPartially.TYPE,
                adaptable -> SubjectsDeletedPartially.of(policyIdFrom(adaptable),
                        deletedSubjectIdsFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static void addSubjectEvents(
            final Map<String, JsonifiableMapper<PolicyEvent<?>>> mappingStrategies) {
        mappingStrategies.put(SubjectCreated.TYPE,
                adaptable -> SubjectCreated.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        subjectFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(SubjectModified.TYPE,
                adaptable -> SubjectModified.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        subjectFrom(adaptable),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
        mappingStrategies.put(SubjectDeleted.TYPE,
                adaptable -> SubjectDeleted.of(policyIdFrom(adaptable),
                        labelFrom(adaptable),
                        entrySubjectIdFromPath(adaptable.getPayload().getPath()),
                        revisionFrom(adaptable),
                        timestampFrom(adaptable),
                        dittoHeadersFrom(adaptable),
                        metadataFrom(adaptable)));
    }

    private static long revisionFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getRevision().orElseThrow(() -> JsonMissingFieldException.newBuilder()
                .fieldName(Payload.JsonFields.REVISION.getPointer().toString()).build());
    }

    @Nullable
    private static Instant timestampFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getTimestamp().orElse(null);
    }

    @Nullable
    private static Metadata metadataFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getMetadata().orElse(null);
    }

}
