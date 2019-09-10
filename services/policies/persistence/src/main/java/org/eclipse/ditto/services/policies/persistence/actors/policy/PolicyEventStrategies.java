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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.services.utils.persistentactors.events.AbstractHandleStrategy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntriesModified;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEntryDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.policies.ResourceCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.policies.ResourceModified;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.eclipse.ditto.signals.events.policies.SubjectModified;
import org.eclipse.ditto.signals.events.policies.SubjectsModified;

/**
 * PersistentActor which "knows" the state of a single {@link org.eclipse.ditto.model.policies.Policy}.
 */
public final class PolicyEventStrategies extends AbstractHandleStrategy<PolicyEvent, Policy> {

    private static final PolicyEventStrategies INSTANCE = new PolicyEventStrategies();

    private PolicyEventStrategies() {
        addStrategy(PolicyCreated.class, new PolicyCreatedStrategy());
        addStrategy(PolicyModified.class, new PolicyModifiedStrategy());
        addStrategy(PolicyDeleted.class, new PolicyDeletedStrategy());
        addStrategy(PolicyEntriesModified.class, new PolicyEntriesModifiedStrategy());
        addStrategy(PolicyEntryCreated.class, new PolicyEntryCreatedStrategy());
        addStrategy(PolicyEntryModified.class, new PolicyEntryModifiedStrategy());
        addStrategy(PolicyEntryDeleted.class, new PolicyEntryDeletedStrategy());
        addStrategy(SubjectsModified.class, new SubjectsModifiedStrategy());
        addStrategy(SubjectCreated.class, new SubjectCreatedStrategy());
        addStrategy(SubjectModified.class, new SubjectModifiedStrategy());
        addStrategy(SubjectDeleted.class, new SubjectDeletedStrategy());
        addStrategy(ResourcesModified.class, new ResourcesModifiedStrategy());
        addStrategy(ResourceCreated.class, new ResourceCreatedStrategy());
        addStrategy(ResourceModified.class, new ResourceModifiedStrategy());
        addStrategy(ResourceDeleted.class, new ResourceDeletedStrategy());
    }

    /**
     * @return the unique {@code PolicyEventStrategies} instance.
     */
    public static PolicyEventStrategies getInstance() {
        return INSTANCE;
    }

    private static final class PolicyCreatedStrategy implements EventStrategy<PolicyCreated, Policy> {

        @Override
        public Policy handle(final PolicyCreated event, @Nullable final Policy entity, final long revision) {
            return event.getPolicy().toBuilder()
                    .setLifecycle(PolicyLifecycle.ACTIVE)
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class PolicyModifiedStrategy implements EventStrategy<PolicyModified, Policy> {

        @Override
        public Policy handle(final PolicyModified event, @Nullable final Policy policy, final long revision) {
            // we need to use the current policy as base otherwise we would loose its state
            final PolicyBuilder copyBuilder = checkNotNull(policy, "policy").toBuilder();

            copyBuilder.removeAll(policy); // remove all old policyEntries!
            copyBuilder.setAll(event.getPolicy().getEntriesSet()); // add the new ones
            return copyBuilder.setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class PolicyDeletedStrategy implements EventStrategy<PolicyDeleted, Policy> {

        @Nullable
        @Override
        public Policy handle(final PolicyDeleted event, @Nullable final Policy policy, final long revision) {
            if (policy != null) {
                return policy.toBuilder()
                        .setLifecycle(PolicyLifecycle.DELETED)
                        .setRevision(revision)
                        .setModified(event.getTimestamp().orElse(null))
                        .build();
            } else {
                return null;
            }
        }
    }

    private static final class PolicyEntriesModifiedStrategy implements EventStrategy<PolicyEntriesModified, Policy> {

        @Nullable
        @Override
        public Policy handle(final PolicyEntriesModified pem, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .removeAll(policy.getEntriesSet())
                    .setAll(pem.getPolicyEntries())
                    .setRevision(revision)
                    .setModified(pem.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class PolicyEntryCreatedStrategy implements EventStrategy<PolicyEntryCreated, Policy> {

        @Override
        public Policy handle(final PolicyEntryCreated pec, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .set(pec.getPolicyEntry())
                    .setRevision(revision)
                    .setModified(pec.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class PolicyEntryModifiedStrategy implements EventStrategy<PolicyEntryModified, Policy> {

        @Override
        public Policy handle(final PolicyEntryModified pem, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .set(pem.getPolicyEntry())
                    .setRevision(revision)
                    .setModified(pem.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class PolicyEntryDeletedStrategy implements EventStrategy<PolicyEntryDeleted, Policy> {

        @Override
        public Policy handle(final PolicyEntryDeleted ped, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .remove(ped.getLabel())
                    .setRevision(revision)
                    .setModified(ped.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class SubjectsModifiedStrategy implements EventStrategy<SubjectsModified, Policy> {

        @Override
        public Policy handle(final SubjectsModified sm, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(sm.getLabel())
                    .map(policyEntry -> PoliciesModelFactory
                            .newPolicyEntry(sm.getLabel(), sm.getSubjects(), policyEntry.getResources()))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(sm.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class SubjectCreatedStrategy implements EventStrategy<SubjectCreated, Policy> {

        @Override
        public Policy handle(final SubjectCreated sc, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(sc.getLabel())
                    .map(policyEntry -> PoliciesModelFactory
                            .newPolicyEntry(sc.getLabel(), policyEntry.getSubjects().setSubject(sc.getSubject()),
                                    policyEntry.getResources()))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(sc.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class SubjectModifiedStrategy implements EventStrategy<SubjectModified, Policy> {

        @Override
        public Policy handle(final SubjectModified sm, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(sm.getLabel())
                    .map(policyEntry -> PoliciesModelFactory
                            .newPolicyEntry(sm.getLabel(), policyEntry.getSubjects().setSubject(sm.getSubject()),
                                    policyEntry.getResources()))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(sm.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class SubjectDeletedStrategy implements EventStrategy<SubjectDeleted, Policy> {

        @Override
        public Policy handle(final SubjectDeleted sd, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .forLabel(sd.getLabel())
                    .removeSubject(sd.getSubjectId())
                    .setRevision(revision)
                    .setModified(sd.getTimestamp().orElse(null))
                    .build();
        }
    }

    private static final class ResourcesModifiedStrategy implements EventStrategy<ResourcesModified, Policy> {

        @Override
        public Policy handle(final ResourcesModified rm, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(rm.getLabel())
                    .map(policyEntry -> PoliciesModelFactory
                            .newPolicyEntry(rm.getLabel(), policyEntry.getSubjects(), rm.getResources()))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(rm.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class ResourceCreatedStrategy implements EventStrategy<ResourceCreated, Policy> {

        @Override
        public Policy handle(final ResourceCreated rc, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(rc.getLabel())
                    .map(policyEntry -> PoliciesModelFactory.newPolicyEntry(rc.getLabel(),
                            policyEntry.getSubjects(),
                            policyEntry.getResources().setResource(rc.getResource())))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(rc.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class ResourceModifiedStrategy implements EventStrategy<ResourceModified, Policy> {

        @Override
        public Policy handle(final ResourceModified rm, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").getEntryFor(rm.getLabel())
                    .map(policyEntry -> PoliciesModelFactory.newPolicyEntry(rm.getLabel(),
                            policyEntry.getSubjects(),
                            policyEntry.getResources().setResource(rm.getResource())))
                    .map(modifiedPolicyEntry -> policy.toBuilder()
                            .set(modifiedPolicyEntry)
                            .setRevision(revision)
                            .setModified(rm.getTimestamp().orElse(null))
                            .build())
                    .orElse(policy);
        }
    }

    private static final class ResourceDeletedStrategy implements EventStrategy<ResourceDeleted, Policy> {

        @Override
        public Policy handle(final ResourceDeleted rd, @Nullable final Policy policy, final long revision) {
            return checkNotNull(policy, "policy").toBuilder()
                    .forLabel(rd.getLabel())
                    .removeResource(rd.getResourceKey())
                    .setRevision(revision)
                    .setModified(rd.getTimestamp().orElse(null))
                    .build();
        }
    }

}
