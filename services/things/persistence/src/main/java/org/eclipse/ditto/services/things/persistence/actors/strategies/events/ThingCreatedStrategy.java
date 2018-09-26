/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingCreated;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.ThingCreated} event.
 */
@Immutable
final class ThingCreatedStrategy implements EventStrategy<ThingCreated> {

    @Override
    public Thing handle(final ThingCreated event, final @Nullable Thing thing, final long revision) {
        return event.getThing()
                .toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }

}
