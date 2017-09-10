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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

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
