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

import org.eclipse.ditto.internal.utils.persistentactors.events.AbstractEventStrategies;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntriesModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportCreated;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportDeleted;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportsModified;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.eclipse.ditto.policies.model.signals.events.ResourceCreated;
import org.eclipse.ditto.policies.model.signals.events.ResourceDeleted;
import org.eclipse.ditto.policies.model.signals.events.ResourceModified;
import org.eclipse.ditto.policies.model.signals.events.ResourcesModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectCreated;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;
import org.eclipse.ditto.policies.model.signals.events.SubjectModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsDeletedPartially;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModified;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;

/**
 * Holds all {@link PolicyEvent} strategies.
 */
public final class PolicyEventStrategies extends AbstractEventStrategies<PolicyEvent<?>, Policy> {

    private static final PolicyEventStrategies INSTANCE = new PolicyEventStrategies();

    private PolicyEventStrategies() {
        addStrategy(PolicyCreated.class, new PolicyCreatedStrategy());
        addStrategy(PolicyModified.class, new PolicyModifiedStrategy());
        addStrategy(PolicyDeleted.class, new PolicyDeletedStrategy());
        addStrategy(PolicyImportsModified.class, new PolicyImportsModifiedStrategy());
        addStrategy(PolicyImportCreated.class, new PolicyImportCreatedStrategy());
        addStrategy(PolicyImportModified.class, new PolicyImportModifiedStrategy());
        addStrategy(PolicyImportDeleted.class, new PolicyImportDeletedStrategy());
        addStrategy(PolicyEntriesModified.class, new PolicyEntriesModifiedStrategy());
        addStrategy(PolicyEntryCreated.class, new PolicyEntryCreatedStrategy());
        addStrategy(PolicyEntryModified.class, new PolicyEntryModifiedStrategy());
        addStrategy(PolicyEntryDeleted.class, new PolicyEntryDeletedStrategy());
        addStrategy(SubjectsModified.class, new SubjectsModifiedStrategy());
        addStrategy(SubjectsModifiedPartially.class, new SubjectsModifiedPartiallyStrategy());
        addStrategy(SubjectCreated.class, new SubjectCreatedStrategy());
        addStrategy(SubjectModified.class, new SubjectModifiedStrategy());
        addStrategy(SubjectDeleted.class, new SubjectDeletedStrategy());
        addStrategy(SubjectsDeletedPartially.class, new SubjectsDeletedPartiallyStrategy());
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
