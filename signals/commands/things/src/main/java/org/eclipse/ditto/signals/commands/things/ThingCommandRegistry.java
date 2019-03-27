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
package org.eclipse.ditto.signals.commands.things;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingCommand}s.
 */
public final class ThingCommandRegistry extends AbstractCommandRegistry<ThingCommand> {

    private ThingCommandRegistry(final Map<String, JsonParsable<ThingCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ThingCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ThingCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingCommand>> parseStrategies = new HashMap<>();

        final ThingModifyCommandRegistry thingModifyCommandRegistry = ThingModifyCommandRegistry.newInstance();
        thingModifyCommandRegistry.getTypes()
                .forEach(type -> parseStrategies.put(type, thingModifyCommandRegistry::parse));

        final ThingQueryCommandRegistry thingQueryCommandRegistry = ThingQueryCommandRegistry.newInstance();
        thingQueryCommandRegistry.getTypes()
                .forEach(type -> parseStrategies.put(type, thingQueryCommandRegistry::parse));

        return new ThingCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingCommand.TYPE_PREFIX;
    }

}
