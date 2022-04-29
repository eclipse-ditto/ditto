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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.events.ThingModified} event.
 */
@Immutable
final class ThingModifiedStrategy extends AbstractThingEventStrategy<ThingModified> {

    ThingModifiedStrategy() {
        super();
    }

    /**
     * Applies the modifications from {@code thingWithModifications} to {@code thingBuilder} by overwriting the
     * existing thing with the modified thing.
     */
    @Override
    protected ThingBuilder.FromCopy applyEvent(final ThingModified event, final ThingBuilder.FromCopy thingBuilder) {

        // we need to use the current thing as base otherwise we would loose its state
        thingBuilder.setLifecycle(ThingLifecycle.ACTIVE)
                .removeAllAttributes()
                .removeAllFeatures()
                .removeDefinition();

        final Thing thingWithModifications = event.getThing();
        thingWithModifications.getPolicyId().ifPresent(thingBuilder::setPolicyId);
        thingWithModifications.getAttributes().ifPresent(thingBuilder::setAttributes);
        thingWithModifications.getDefinition().ifPresent(thingBuilder::setDefinition);
        thingWithModifications.getFeatures().ifPresent(thingBuilder::setFeatures);

        return thingBuilder;
    }

}
