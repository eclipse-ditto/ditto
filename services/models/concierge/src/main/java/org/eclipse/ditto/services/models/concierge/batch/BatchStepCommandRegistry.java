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
package org.eclipse.ditto.services.models.concierge.batch;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandRegistry;

/**
 * A {@link CommandRegistry} for commands which are applicable as batch steps.
 */
@Immutable
public final class BatchStepCommandRegistry extends AbstractCommandRegistry<Command> {

    private BatchStepCommandRegistry(final Map<String, JsonParsable<Command>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * @return the command registry.
     */
    public static BatchStepCommandRegistry newInstance() {
        final Map<String, JsonParsable<Command>> parseStrategies =
                toParseStrategies(ThingCommandRegistry.newInstance(), SudoCommandRegistry.newInstance());

        return new BatchStepCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingCommand.TYPE_PREFIX;
    }

    private static Map<String, JsonParsable<Command>> toParseStrategies(final CommandRegistry<?>... commandRegistries) {
        final Map<String, JsonParsable<Command>> parseStrategies = new HashMap<>();
        for (CommandRegistry<?> commandRegistry : commandRegistries) {
            commandRegistry.getTypes().forEach(type -> parseStrategies.put(type, commandRegistry::parse));
        }
        return parseStrategies;
    }

}
