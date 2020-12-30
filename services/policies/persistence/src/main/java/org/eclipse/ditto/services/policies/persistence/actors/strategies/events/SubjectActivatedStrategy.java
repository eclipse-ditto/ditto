/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Instant;

import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.signals.events.policies.SubjectActivated;

/**
 * This strategy handles {@link org.eclipse.ditto.signals.events.policies.SubjectActivated} events.
 */
final class SubjectActivatedStrategy extends AbstractPolicyEventStrategy<SubjectActivated> {

    @Override
    protected PolicyBuilder applyEvent(final SubjectActivated event, final PolicyBuilder policyBuilder) {
        final Instant now = Instant.now();
        final boolean isExpiryAfterNow =
                event.getSubject().getExpiry().map(expiry -> expiry.getTimestamp().isAfter(now)).orElse(false);
        if (isExpiryAfterNow) {
            return policyBuilder.setSubjectFor(event.getLabel(), event.getSubject());
        } else {
            return policyBuilder;
        }
    }
}
