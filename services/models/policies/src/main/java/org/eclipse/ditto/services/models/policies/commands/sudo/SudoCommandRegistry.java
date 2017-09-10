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
package org.eclipse.ditto.services.models.policies.commands.sudo;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link SudoCommand}s.
 */
@Immutable
public class SudoCommandRegistry extends AbstractCommandRegistry<SudoCommand> {

    /**
     * Constructs a new {@code SudoCommandRegistry} for the specified {@code parseStrategies}.
     *
     * @param parseStrategies the parse strategies.
     */
    protected SudoCommandRegistry(final Map<String, JsonParsable<SudoCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SudoCommandRegistry}.
     *
     * @return the command registry.
     */
    public static SudoCommandRegistry newInstance() {
        final Map<String, JsonParsable<SudoCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(SudoRetrievePolicy.TYPE, SudoRetrievePolicy::fromJson);

        return new SudoCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return SudoCommand.TYPE_PREFIX;
    }
}
