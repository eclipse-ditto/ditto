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

import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommandResponseRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponseRegistry;

/**
 * A {@link CommandResponseRegistry} for all command responses by batch steps.
 */
public final class BatchStepCommandResponseRegistry extends AbstractCommandResponseRegistry<CommandResponse> {

    private BatchStepCommandResponseRegistry(final Map<String, JsonParsable<CommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * @return the commandResponse registry.
     */
    public static BatchStepCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<CommandResponse>> parseStrategies =
                toParseStrategies(ThingCommandResponseRegistry.newInstance(),
                        SudoCommandResponseRegistry.newInstance());

        return new BatchStepCommandResponseRegistry(parseStrategies);
    }

    private static Map<String, JsonParsable<CommandResponse>> toParseStrategies(
            final CommandResponseRegistry<?>... commandResponseRegistries) {
        final Map<String, JsonParsable<CommandResponse>> parseStrategies = new HashMap<>();
        for (CommandResponseRegistry<?> commandRegistry : commandResponseRegistries) {
            commandRegistry.getTypes().forEach(type -> parseStrategies.put(type, commandRegistry::parse));
        }
        return parseStrategies;
    }

}
