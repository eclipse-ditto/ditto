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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * A registry that maps a command to its according exception (access, modify).
 *
 * @param <C> the type of the command.
 * @param <T> the type of the exception.
 */
@FunctionalInterface
public interface CommandToExceptionRegistry<C extends Command, T extends DittoRuntimeException> {

    /**
     * Maps a passed in command to an exception.
     *
     * @param command the command to create the exception from
     * @return the created exception
     */
    T exceptionFrom(C command);

}
