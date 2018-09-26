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

import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated} event.
 */
@Immutable
final class FeaturePropertiesCreatedStrategy extends AbstractEventStrategy<FeaturePropertiesCreated> {

    @Override
    protected ThingBuilder.FromCopy applyEvent(final FeaturePropertiesCreated event,
            final ThingBuilder.FromCopy thingBuilder) {

        return thingBuilder.setFeatureProperties(event.getFeatureId(), event.getProperties());
    }

}
