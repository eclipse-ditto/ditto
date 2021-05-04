/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.time.Instant;

import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;

/**
 * This strategy handles {@link org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially} events.
 */
final class SubjectsModifiedPartiallyStrategy extends AbstractPolicyEventStrategy<SubjectsModifiedPartially> {

    @Override
    protected PolicyBuilder applyEvent(final SubjectsModifiedPartially event, final PolicyBuilder policyBuilder) {
        final Instant now = Instant.now();
        event.getModifiedSubjects().forEach((label, subjects) ->
                subjects.forEach(subject -> {
                    final boolean isSubjectExpiryAfterNow = subject.getExpiry()
                            .map(expiry -> expiry.getTimestamp().isAfter(now))
                            .orElse(false);
                    if (isSubjectExpiryAfterNow) {
                        policyBuilder.setSubjectFor(label, subject);
                    }
                })
        );
        return policyBuilder;
    }
}
