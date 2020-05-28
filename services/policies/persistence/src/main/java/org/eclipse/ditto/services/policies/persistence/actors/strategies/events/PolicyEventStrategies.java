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

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistentactors.events.AbstractEventStrategies;
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
public final class PolicyEventStrategies extends AbstractEventStrategies<PolicyEvent, Policy> {

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

}
