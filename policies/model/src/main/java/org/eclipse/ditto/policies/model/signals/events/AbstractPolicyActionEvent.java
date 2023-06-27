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
package org.eclipse.ditto.policies.model.signals.events;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * Abstract base class of a {@link PolicyActionEvent}.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
abstract class AbstractPolicyActionEvent<T extends AbstractPolicyActionEvent<T>>
        extends AbstractPolicyEvent<T> implements PolicyActionEvent<T> {

    /**
     * Constructs a new {@code AbstractPolicyActionEvent} object.
     *
     * @param type the type of this event.
     * @param policyId the TYPE of the Policy with which this event is associated.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractPolicyActionEvent(final String type,
            final PolicyId policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(type, policyId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Aggregate a policy action event for subject creation or modification with similar events.
     *
     * @param initialModifiedSubjects subjects created or modified by this event indexed by their policy entry labels.
     * @param otherEvents other subject creation or modification events.
     * @return the aggregated event.
     */
    protected SubjectsModifiedPartially aggregateWithSubjectCreatedOrModified(
            final Map<Label, Collection<Subject>> initialModifiedSubjects,
            final Collection<PolicyActionEvent<?>> otherEvents) {

        final Map<Label, Collection<Subject>> modifiedSubjects = new LinkedHashMap<>(initialModifiedSubjects);
        for (final PolicyActionEvent<?> event : otherEvents) {
            if (event instanceof SubjectCreated) {
                final SubjectCreated subjectCreated = (SubjectCreated) event;
                final Set<Subject> mergedSubjects =
                        append(modifiedSubjects.get(subjectCreated.getLabel()), subjectCreated.getSubject());
                modifiedSubjects.put(subjectCreated.getLabel(), mergedSubjects);
            } else if (event instanceof SubjectModified) {
                final SubjectModified subjectModified = (SubjectModified) event;
                final Set<Subject> mergedSubjects =
                        append(modifiedSubjects.get(subjectModified.getLabel()), subjectModified.getSubject());
                modifiedSubjects.put(subjectModified.getLabel(), mergedSubjects);
            } else if (event instanceof SubjectsModifiedPartially) {
                final SubjectsModifiedPartially subjectsModifiedPartially = (SubjectsModifiedPartially) event;
                appendValues(modifiedSubjects, subjectsModifiedPartially.getModifiedSubjects());
            }
        }
        return SubjectsModifiedPartially.of(getPolicyEntityId(), modifiedSubjects, getRevision(),
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    /**
     * Aggregate a policy action event for subject deletion.
     *
     * @param initialDeletedSubjectIds Subject IDs deleted by this event indexed by their policy entry labels.
     * @param otherEvents other subject deletion events.
     * @return the aggregated event.
     */
    protected SubjectsDeletedPartially aggregateWithSubjectDeleted(
            final Map<Label, Collection<SubjectId>> initialDeletedSubjectIds,
            final Collection<PolicyActionEvent<?>> otherEvents) {

        final Map<Label, Collection<SubjectId>> deletedSubjectIds = new LinkedHashMap<>(initialDeletedSubjectIds);
        for (final PolicyActionEvent<?> event : otherEvents) {
            if (event instanceof SubjectDeleted) {
                final SubjectDeleted subjectDeleted = (SubjectDeleted) event;
                final Collection<SubjectId> existingSubjectIds = deletedSubjectIds.get(subjectDeleted.getLabel());
                final Set<SubjectId> mergedSubjectIds;
                if (null == existingSubjectIds) {
                    mergedSubjectIds = new LinkedHashSet<>();
                } else {
                    mergedSubjectIds = new LinkedHashSet<>(existingSubjectIds);
                }
                mergedSubjectIds.add(subjectDeleted.getSubjectId());
                deletedSubjectIds.put(subjectDeleted.getLabel(), mergedSubjectIds);
            } else if (event instanceof SubjectsDeletedPartially) {
                final SubjectsDeletedPartially subjectsDeletedPartially = (SubjectsDeletedPartially) event;
                appendValues(deletedSubjectIds, subjectsDeletedPartially.getDeletedSubjectIds());
            }
        }
        return SubjectsDeletedPartially.of(getPolicyEntityId(), deletedSubjectIds, getRevision(),
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    private static <T> LinkedHashSet<T> append(@Nullable final Collection<T> existingItems, final T newItem) {
        final LinkedHashSet<T> mergedItems = new LinkedHashSet<>();
        if (null != existingItems) {
            mergedItems.addAll(existingItems);
        }
        mergedItems.add(newItem);
        return mergedItems;
    }

    private static <K, V> void appendValues(final Map<K, Collection<V>> accumulator,
            final Map<K, Collection<V>> toMerge) {

        toMerge.forEach((key, valueToMerge) -> accumulator.compute(key, (k, value) -> {
            if (value == null) {
                return valueToMerge;
            } else {
                final LinkedHashSet<V> mergedValue = new LinkedHashSet<>(value);
                mergedValue.addAll(valueToMerge);
                return mergedValue;
            }
        }));
    }
}
