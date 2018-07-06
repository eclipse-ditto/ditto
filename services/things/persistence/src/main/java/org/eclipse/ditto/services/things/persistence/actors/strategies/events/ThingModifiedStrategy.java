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
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.ThingModified} event.
 */
@ThreadSafe
final class ThingModifiedStrategy implements EventStrategy<ThingModified> {

    @Override
    public Thing handle(final ThingModified event, final Thing thing, final long revision) {
        // we need to use the current thing as base otherwise we would loose its state
        final ThingBuilder.FromCopy copyBuilder = thing.toBuilder().setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(revision)
                .setModified(event.getTimestamp().orElse(null));

        mergeThingModifications(event.getThing(), copyBuilder);

        return copyBuilder.build();
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of {@code
     * builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in {@code
     * builder} remains unchanged.
     *
     * @param thingWithModifications the thing containing the modifications.
     * @param builder the builder to be modified.
     */
    private static void mergeThingModifications(final Thing thingWithModifications,
            final ThingBuilder.FromCopy builder) {
        thingWithModifications.getPolicyId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);
    }

}
