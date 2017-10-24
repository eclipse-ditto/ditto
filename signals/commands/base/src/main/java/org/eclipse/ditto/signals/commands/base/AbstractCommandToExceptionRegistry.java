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
package org.eclipse.ditto.signals.commands.base;

import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Base class for all registries which map a command to its according exception (access, modify).
 */
public abstract class AbstractCommandToExceptionRegistry<C extends Command, T extends DittoRuntimeException>
        implements CommandToExceptionRegistry<C, T> {

    private final Map<String, Function<C, T>> mappingStrategies;

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies for this registry
     */
    public AbstractCommandToExceptionRegistry(final Map<String, Function<C, T>> mappingStrategies) {
        this.mappingStrategies = mappingStrategies;
    }

    /**
     * Fallback exception mapping for commands not found in {@code mappingStrategies}.
     *
     * @param command The command to map.
     * @return The exception corresponding to the command.
     */
    protected T fallback(final C command) {
        throw new IllegalArgumentException(
                "No exception mapping found for the passed-in Command: " + command.getType());
    }

    @Override
    public T exceptionFrom(final C command) {
        final Function<C, T> mapper = mappingStrategies.get(command.getType());
        if (mapper != null) {
            return mapper.apply(command);
        } else {
            return fallback(command);
        }
    }

}
