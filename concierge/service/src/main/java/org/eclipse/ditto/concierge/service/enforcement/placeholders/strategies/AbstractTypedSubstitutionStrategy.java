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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;

/**
 * Abstract base class for instances of {@link SubstitutionStrategy} which matches on a concrete subtype of
 * {@link DittoHeadersSettable}.
 *
 * @param <T> the subtype of {@link DittoHeadersSettable} handled by this strategy.
 */
abstract class AbstractTypedSubstitutionStrategy<T extends DittoHeadersSettable<?>> implements SubstitutionStrategy<T> {

    private final Class<T> type;

    AbstractTypedSubstitutionStrategy(final Class<T> type) {
        this.type = requireNonNull(type);
    }

    @Override
    public boolean matches(final DittoHeadersSettable<?> dittoHeadersSettable) {
        return type.isAssignableFrom(dittoHeadersSettable.getClass());
    }

    static Subjects substituteSubjects(final Subjects subjects,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm, final DittoHeaders dittoHeaders) {
        Subjects newSubjects = subjects;
        for (final Subject subject : subjects) {
            final String subjectId = subject.getId().toString();
            final String substitutedSubjectId = substitutionAlgorithm.substitute(subjectId, dittoHeaders);

            if (!subjectId.equals(substitutedSubjectId)) {
                final Subject newSubject =
                        Subject.newInstance(substitutedSubjectId, subject.getType());

                newSubjects = newSubjects.removeSubject(subjectId).setSubject(newSubject);
            }
        }

        return newSubjects;
    }

    static PolicyEntry substitutePolicyEntry(final PolicyEntry existingPolicyEntry,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm, final DittoHeaders dittoHeaders) {
        final Subjects existingSubjects = existingPolicyEntry.getSubjects();
        final Subjects substitutedSubjects = substituteSubjects(existingSubjects, substitutionAlgorithm, dittoHeaders);

        final PolicyEntry resultEntry;
        if (existingSubjects.equals(substitutedSubjects)) {
            resultEntry = existingPolicyEntry;
        } else {
            resultEntry = PolicyEntry.newInstance(existingPolicyEntry.getLabel(), substitutedSubjects,
                    existingPolicyEntry.getResources());
        }

        return resultEntry;
    }

    static Policy substitutePolicy(final Policy policy,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final DittoHeaders dittoHeaders) {
        final Iterable<PolicyEntry> existingEntries = policy.getEntriesSet();
        final Iterable<PolicyEntry> substitutedEntries =
                substitutePolicyEntries(existingEntries, substitutionAlgorithm, dittoHeaders);

        final Policy resultPolicy;
        if (existingEntries.equals(substitutedEntries)) {
            resultPolicy = policy;
        } else {
            resultPolicy = PoliciesModelFactory.newPolicyBuilder(policy).setAll(substitutedEntries).build();
        }

        return resultPolicy;
    }


    static Iterable<PolicyEntry> substitutePolicyEntries(final Iterable<PolicyEntry> policyEntries,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final DittoHeaders dittoHeaders) {
        final Iterator<PolicyEntry> existingEntriesIterator = policyEntries.iterator();

        Iterable<PolicyEntry> currentEntries = policyEntries;
        for (int i = 0; existingEntriesIterator.hasNext(); i++) {
            final PolicyEntry existingEntry = existingEntriesIterator.next();
            final PolicyEntry substitutedEntry =
                    substitutePolicyEntry(existingEntry, substitutionAlgorithm, dittoHeaders);
            if (!existingEntry.equals(substitutedEntry)) {
                currentEntries = replace(currentEntries, substitutedEntry, i);
            }
        }

        return currentEntries;
    }

    private static Iterable<PolicyEntry> replace(final Iterable<PolicyEntry> entries,
            final PolicyEntry substitutedEntry, final int index) {
        final List<PolicyEntry> newEntries = new ArrayList<>();

        final Iterator<PolicyEntry> entriesIterator = entries.iterator();
        for (int i = 0; entriesIterator.hasNext(); i++) {
            final PolicyEntry existingEntry = entriesIterator.next();

            if (i == index) {
                newEntries.add(substitutedEntry);
            } else {
                newEntries.add(existingEntry);
            }
        }

        return newEntries;
    }

}
