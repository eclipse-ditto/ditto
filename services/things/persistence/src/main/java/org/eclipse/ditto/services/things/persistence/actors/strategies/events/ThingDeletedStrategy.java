/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingDeleted;

/**
 * TODO javadoc
 */
final class ThingDeletedStrategy implements HandleStrategy<ThingDeleted> {

    @Override
    public Thing handle(final ThingDeleted event, final Thing thing, final long revision) {
        if (thing != null) {
            return thing.toBuilder()
                    .setLifecycle(ThingLifecycle.DELETED)
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .build();
        } else {
            // TODO think about logging in event strategies
            // log.warning("Thing was null when 'ThingDeleted' event should have been applied on recovery.");
            return null;
        }
    }

}
