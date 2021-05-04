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

import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;

/**
 * This strategy handles {@link org.eclipse.ditto.policies.model.signals.events.SubjectCreated} events.
 */
final class SubjectCreatedStrategy extends AbstractPolicyEventStrategy<SubjectCreated> {

    @Override
    protected PolicyBuilder applyEvent(final SubjectCreated sc, final PolicyBuilder policyBuilder) {
        return policyBuilder.setSubjectFor(sc.getLabel(), sc.getSubject());
    }

}
