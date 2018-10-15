/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.HeaderBasedPlaceholderSubstitutionAlgorithm;

/**
 * Abstract base class for instances of {@link SubstitutionStrategy} which matches on a concrete subtype of
 * {@link WithDittoHeaders}.
 * @param <T> the subtype of {@link WithDittoHeaders} handled by this strategy.
 */
abstract class AbstractTypedSubstitutionStrategy<T extends WithDittoHeaders> implements SubstitutionStrategy<T> {
    private final Class<T> type;

    AbstractTypedSubstitutionStrategy(final Class<T> type) {
        this.type = requireNonNull(type);
    }

    @Override
    public boolean matches(final WithDittoHeaders withDittoHeaders) {
        return type.isAssignableFrom(withDittoHeaders.getClass());
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

    static AclEntry substituteAclEntry(final AclEntry existingAclEntry,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm, final DittoHeaders dittoHeaders) {

        final String subjectId = existingAclEntry.getAuthorizationSubject().getId();
        final String substitutedSubjectId = substitutionAlgorithm.substitute(subjectId, dittoHeaders);

        final AclEntry resultAclEntry;
        if (subjectId.equals(substitutedSubjectId)) {
            resultAclEntry = existingAclEntry;
        } else {
            resultAclEntry = AclEntry.newInstance(AuthorizationSubject.newInstance(substitutedSubjectId),
                    existingAclEntry.getPermissions());
        }

        return resultAclEntry;
    }

    static AccessControlList substituteAcl(final AccessControlList acl,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm, final DittoHeaders dittoHeaders) {
        AccessControlList newAcl = acl;
        for (final AclEntry aclEntry : acl) {
            final AclEntry substitutedEntry = substituteAclEntry(aclEntry, substitutionAlgorithm, dittoHeaders);

            if (!aclEntry.equals(substitutedEntry)) {
                newAcl = newAcl.removeEntry(aclEntry).setEntry(substitutedEntry);
            }
        }

        return newAcl;
    }

    static Thing substituteThing(final Thing existingThing,
            final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm, final DittoHeaders dittoHeaders) {

        final AccessControlList existingAcl = existingThing.getAccessControlList().orElse(null);
        if (existingAcl == null) {
            return existingThing;
        }

        final AccessControlList substitutedAcl = substituteAcl(existingAcl, substitutionAlgorithm, dittoHeaders);

        if (existingAcl.equals(substitutedAcl)) {
            return existingThing;
        } else {
            return ThingsModelFactory.newThingBuilder(existingThing)
                    .removeAllPermissions()
                    .setPermissions(substitutedAcl).build();
        }
    }

}
