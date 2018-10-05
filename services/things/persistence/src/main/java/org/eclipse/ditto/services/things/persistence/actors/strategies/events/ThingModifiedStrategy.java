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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.ThingModified} event.
 */
@Immutable
final class ThingModifiedStrategy extends AbstractEventStrategy<ThingModified> {

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code thingBuilder}.
     * Merge is implemented very simple: All first level fields of {@code thingWithModifications} overwrite the first
     * level fields of {@code thingBuilder}.
     * If a field does not exist in the event's Thing, a maybe existing field in {@code thingBuilder} remains
     * unchanged.
     */
    @Override
    protected ThingBuilder.FromCopy applyEvent(final ThingModified event, final ThingBuilder.FromCopy thingBuilder) {

        // we need to use the current thing as base otherwise we would loose its state
        thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);

        final Thing thingWithModifications = event.getThing();
        thingWithModifications.getPolicyId().ifPresent(thingBuilder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(thingBuilder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(thingBuilder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(thingBuilder::setFeatures);

        return thingBuilder;
    }

}
