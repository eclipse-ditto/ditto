/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;

/**
 * This strategy handles the {@link ThingCreated} event.
 */
@Immutable
final class ThingCreatedStrategy extends AbstractThingEventStrategy<ThingCreated> {

    @Override
    public Thing handle(final ThingCreated event, final @Nullable Thing thing, final long revision) {
        return event.getThing()
                .toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(event.getTimestamp().orElseGet(() -> event.getThing().getModified().orElse(null)))
                .setCreated(event.getTimestamp().orElseGet(() -> event.getThing().getCreated().orElse(null)))
                .setMetadata(event.getMetadata().orElseGet(() -> event.getThing().getMetadata().orElse(null)))
                .build();
    }

}
