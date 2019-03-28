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
package org.eclipse.ditto.services.models.things.commands.sudo;

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

        parseStrategies.put(SudoRetrieveThing.TYPE, SudoRetrieveThing::fromJson);
        parseStrategies.put(SudoRetrieveThings.TYPE, SudoRetrieveThings::fromJson);

        return new SudoCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return SudoCommand.TYPE_PREFIX;
    }
}
