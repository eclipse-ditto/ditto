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
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted} event.
 */
@ThreadSafe
final class FeaturePropertyDeletedStrategy implements HandleStrategy<FeaturePropertyDeleted> {

    @Override
    public Thing handle(final FeaturePropertyDeleted event, final Thing thing, final long revision) {
        return thing.toBuilder()
                .removeFeatureProperty(event.getFeatureId(), event.getPropertyPointer())
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null))
                .build();
    }

}
