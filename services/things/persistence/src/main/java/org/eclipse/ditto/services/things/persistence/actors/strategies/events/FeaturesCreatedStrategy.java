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

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.FeaturesCreated} event.
 */
@ThreadSafe
final class FeaturesCreatedStrategy implements EventStrategy<FeaturesCreated> {

    @Override
    public Thing handle(final FeaturesCreated event, final Thing thing, final long revision) {
        return thing.toBuilder()
                .setFeatures(event.getFeatures())
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }

}
