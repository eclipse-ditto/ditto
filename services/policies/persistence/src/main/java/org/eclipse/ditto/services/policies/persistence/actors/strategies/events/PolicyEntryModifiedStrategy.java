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

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;

/**
 * This strategy handles {@link org.eclipse.ditto.signals.events.policies.PolicyEntryModified} events.
 */
final class PolicyEntryModifiedStrategy implements EventStrategy<PolicyEntryModified, Policy> {

    @Override
    public Policy handle(final PolicyEntryModified pem, @Nullable final Policy policy, final long revision) {
        return checkNotNull(policy, "policy").toBuilder()
                .set(pem.getPolicyEntry())
                .setRevision(revision)
                .setModified(pem.getTimestamp().orElse(null))
                .build();
    }
}
