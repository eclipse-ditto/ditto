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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * Base class for all registries which map a command to its according exception (access, modify).
 */
public abstract class AbstractCommandToExceptionRegistry<C extends Command<?>, T extends DittoRuntimeException>
        implements CommandToExceptionRegistry<C, T> {

    private final Map<String, Function<C, T>> mappingStrategies;

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies for this registry
     */
    protected AbstractCommandToExceptionRegistry(final Map<String, Function<C, T>> mappingStrategies) {
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
