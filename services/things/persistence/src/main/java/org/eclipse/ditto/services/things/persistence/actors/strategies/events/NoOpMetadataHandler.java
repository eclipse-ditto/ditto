/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import javax.annotation.concurrent.Immutable;

/**
 * Metadata Handler implementation of {@code MetadataHandler} that does nothing, ideally for testing.
 * @param <T> Event Type
 */
@Immutable
class NoOpMetadataHandler<T extends ThingEvent<T>> implements MetadataHandler<T> {

    @Override
    public ThingBuilder.FromCopy handle(T event, Thing thing, ThingBuilder.FromCopy builder) {
        return builder;
    }
}
