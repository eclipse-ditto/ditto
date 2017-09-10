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
package org.eclipse.ditto.signals.commands.thingsearch;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingSearchCommand}s.
 */
@Immutable
public final class ThingSearchCommandRegistry extends AbstractCommandRegistry<ThingSearchCommand> {

    private ThingSearchCommandRegistry(final Map<String, JsonParsable<ThingSearchCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SearchCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ThingSearchCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingSearchCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(QueryThings.TYPE, QueryThings::fromJson);
        parseStrategies.put(CountThings.TYPE, CountThings::fromJson);

        return new ThingSearchCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingSearchCommand.TYPE_PREFIX;
    }

}
