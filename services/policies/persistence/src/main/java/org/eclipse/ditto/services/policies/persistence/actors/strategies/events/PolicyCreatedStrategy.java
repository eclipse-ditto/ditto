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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;

/**
 * This strategy handles {@link org.eclipse.ditto.signals.events.policies.PolicyCreated} events.
 */
final class PolicyCreatedStrategy implements EventStrategy<PolicyCreated, Policy> {

    @Override
    public Policy handle(final PolicyCreated event, @Nullable final Policy entity, final long revision) {
        return event.getPolicy().toBuilder()
                .setLifecycle(PolicyLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }
}
