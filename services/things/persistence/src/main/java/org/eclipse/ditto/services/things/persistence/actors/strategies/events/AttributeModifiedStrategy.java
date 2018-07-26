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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.signals.events.things.AttributeModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.AttributeModified} event.
 */
@Immutable
final class AttributeModifiedStrategy extends AbstractEventStrategy<AttributeModified> {

    @Override
    protected ThingBuilder.FromCopy applyEvent(final AttributeModified event,
            final ThingBuilder.FromCopy thingBuilder) {

        return thingBuilder.setAttribute(event.getAttributePointer(), event.getAttributeValue());
    }

}
