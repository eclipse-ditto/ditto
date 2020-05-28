/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.events;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;

/**
 * This strategy handles {@link org.eclipse.ditto.signals.events.policies.SubjectCreated} events.
 */
final class SubjectCreatedStrategy implements EventStrategy<SubjectCreated, Policy> {

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
