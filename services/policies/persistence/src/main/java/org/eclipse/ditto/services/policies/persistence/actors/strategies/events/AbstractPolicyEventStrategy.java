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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

@Immutable
abstract class AbstractPolicyEventStrategy<T extends PolicyEvent<T>> implements EventStrategy<T, Policy> {
    /**
     * Constructs a new {@code AbstractEventStrategy} object.
     */
    protected AbstractPolicyEventStrategy() {
        super();
    }

    @Nullable
    @Override
    public Policy handle(final T event, @Nullable final Policy policy, final long revision) {
        if (null != policy) {
            PolicyBuilder policyBuilder = policy.toBuilder()
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null));
            policyBuilder = applyEvent(event, policyBuilder);
            return policyBuilder.build();
        }
        return null;
    }

    /**
     * Apply the specified event to the also specified PolicyBuilder. The builder has already the specified revision
     * set as well as the event's timestamp.
     *
     * @param event the ThingEvent to be applied.
     * @param policyBuilder builder which is derived from the {@code event}'s Thing with the revision and event
     * timestamp already set.
     * @return the updated {@code policyBuilder} after applying {@code event}.
     */
    protected PolicyBuilder applyEvent(final T event, final PolicyBuilder policyBuilder) {
        return policyBuilder;
    }
}
