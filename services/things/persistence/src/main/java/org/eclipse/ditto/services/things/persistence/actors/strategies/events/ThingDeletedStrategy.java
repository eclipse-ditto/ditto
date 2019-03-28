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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.events.things.ThingDeleted} event.
 */
@Immutable
final class ThingDeletedStrategy extends AbstractEventStrategy<ThingDeleted> {

    @Override
    protected ThingBuilder.FromCopy applyEvent(final ThingDeleted event, final ThingBuilder.FromCopy thingBuilder) {
        return thingBuilder.setLifecycle(ThingLifecycle.DELETED);
    }

}
