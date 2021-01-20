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
package org.eclipse.ditto.signals.events.policies;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;

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
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractPolicyActionEvent(final String type, final PolicyId policyId, final long revision,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(type, policyId, revision, timestamp, dittoHeaders);
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

        final Map<Label, Collection<Subject>> modifiedSubjects = new HashMap<>(initialModifiedSubjects);
        for (final PolicyActionEvent<?> event : otherEvents) {
            if (event instanceof SubjectCreated) {
                final SubjectCreated subjectCreated = (SubjectCreated) event;
                final Collection<Subject> existingSubjects = modifiedSubjects.get(subjectCreated.getLabel());
                final Set<Subject> mergedSubjects;
                if (null == existingSubjects) {
                    mergedSubjects = new LinkedHashSet<>();
                } else {
                    mergedSubjects = new LinkedHashSet<>(existingSubjects);
                }
                mergedSubjects.add(subjectCreated.getSubject());
                modifiedSubjects.put(subjectCreated.getLabel(), mergedSubjects);
            } else if (event instanceof SubjectModified) {
                final SubjectModified subjectModified = (SubjectModified) event;
                final Collection<Subject> existingSubjects = modifiedSubjects.get(subjectModified.getLabel());
                final Set<Subject> mergedSubjects;
                if (null == existingSubjects) {
                    mergedSubjects = new LinkedHashSet<>();
                } else {
                    mergedSubjects = new LinkedHashSet<>(existingSubjects);
                }
                mergedSubjects.add(subjectModified.getSubject());
                modifiedSubjects.put(subjectModified.getLabel(), mergedSubjects);
            }
        }
        return SubjectsModifiedPartially.of(getPolicyEntityId(), modifiedSubjects, getRevision(),
                getTimestamp().orElse(null), getDittoHeaders());
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

        final Map<Label, Collection<SubjectId>> deletedSubjectIds = new HashMap<>(initialDeletedSubjectIds);
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
            }
        }
        return SubjectsDeletedPartially.of(getPolicyEntityId(), deletedSubjectIds, getRevision(),
                getTimestamp().orElse(null), getDittoHeaders());
    }
}
