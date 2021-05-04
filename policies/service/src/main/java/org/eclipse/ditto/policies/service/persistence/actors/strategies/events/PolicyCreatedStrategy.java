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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.events;

import javax.annotation.Nullable;

import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;

/**
 * This strategy handles {@link org.eclipse.ditto.policies.model.signals.events.PolicyCreated} events.
 */
final class PolicyCreatedStrategy implements EventStrategy<PolicyCreated, Policy> {

    @Override
    public Policy handle(final PolicyCreated event, @Nullable final Policy entity, final long revision) {
        return event.getPolicy().toBuilder()
                .setLifecycle(PolicyLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(event.getTimestamp().orElseGet(() -> event.getPolicy().getModified().orElse(null)))
                .setCreated(event.getTimestamp().orElseGet(() -> event.getPolicy().getCreated().orElse(null)))
                .setMetadata(event.getMetadata().orElseGet(() -> event.getPolicy().getMetadata().orElse(null)))
                .build();
    }
}
