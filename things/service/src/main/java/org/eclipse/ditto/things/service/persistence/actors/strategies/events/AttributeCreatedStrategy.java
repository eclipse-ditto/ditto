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

import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.signals.events.AttributeCreated;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.events.AttributeCreated} event.
 */
@Immutable
final class AttributeCreatedStrategy extends AbstractThingEventStrategy<AttributeCreated> {

    protected AttributeCreatedStrategy() {
        super();
    }

    @Override
    protected ThingBuilder.FromCopy applyEvent(final AttributeCreated event, final ThingBuilder.FromCopy thingBuilder) {
        return thingBuilder.setAttribute(event.getAttributePointer(), event.getAttributeValue());
    }

}
