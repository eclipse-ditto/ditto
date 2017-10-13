/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.base;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;


/**
 * Abstract implementation of {@link EventRegistry}.
 *
 * @param <T> the type of the Event to parse.
 */
@Immutable
public abstract class AbstractEventRegistry<T extends Event> extends AbstractJsonParsableRegistry<T>
        implements EventRegistry<T> {

    /**
     * Constructs a new {@code AbstractEventRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected AbstractEventRegistry(final Map<String, JsonParsable<T>> parseStrategies) {
        super(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(Event.JsonFields.TYPE);
    }

}
