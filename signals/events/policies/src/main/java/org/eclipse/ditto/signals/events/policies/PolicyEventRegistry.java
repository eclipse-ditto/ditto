/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.policies;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.events.base.AbstractEventRegistry;
import org.eclipse.ditto.signals.events.base.EventRegistry;

/**
 * An {@link EventRegistry} aware of all {@link PolicyEvent}s.
 */
@Immutable
public final class PolicyEventRegistry extends AbstractEventRegistry<PolicyEvent> {

    private PolicyEventRegistry(final Map<String, JsonParsable<PolicyEvent>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code PolicyEventRegistry}.
     *
     * @return the event registry.
     */
    public static PolicyEventRegistry newInstance() {
        final Map<String, JsonParsable<PolicyEvent>> parseStrategies = new HashMap<>();

        parseStrategies.put(PolicyCreated.TYPE, PolicyCreated::fromJson);
        parseStrategies.put(PolicyModified.TYPE, PolicyModified::fromJson);
        parseStrategies.put(PolicyDeleted.TYPE, PolicyDeleted::fromJson);

        parseStrategies.put(PolicyEntriesModified.TYPE, PolicyEntriesModified::fromJson);

        parseStrategies.put(PolicyEntryCreated.TYPE, PolicyEntryCreated::fromJson);
        parseStrategies.put(PolicyEntryModified.TYPE, PolicyEntryModified::fromJson);
        parseStrategies.put(PolicyEntryDeleted.TYPE, PolicyEntryDeleted::fromJson);

        parseStrategies.put(SubjectsModified.TYPE, SubjectsModified::fromJson);
        parseStrategies.put(SubjectCreated.TYPE, SubjectCreated::fromJson);
        parseStrategies.put(SubjectModified.TYPE, SubjectModified::fromJson);
        parseStrategies.put(SubjectDeleted.TYPE, SubjectDeleted::fromJson);

        parseStrategies.put(ResourcesModified.TYPE, ResourcesModified::fromJson);
        parseStrategies.put(ResourceCreated.TYPE, ResourceCreated::fromJson);
        parseStrategies.put(ResourceModified.TYPE, ResourceModified::fromJson);
        parseStrategies.put(ResourceDeleted.TYPE, ResourceDeleted::fromJson);

        return new PolicyEventRegistry(parseStrategies);
    }

}
