/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.results;

import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.Event;

/**
 * Result signifying an error.
 */
public final class ErrorResult<E extends Event<?>> implements Result<E> {

    private final Command<?> errorCausingCommand;
    private final DittoRuntimeException dittoRuntimeException;

    ErrorResult(final DittoRuntimeException dittoRuntimeException, final Command<?> errorCausingCommand) {
        this.dittoRuntimeException = dittoRuntimeException;
        this.errorCausingCommand = errorCausingCommand;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "dittoRuntimeException=" + dittoRuntimeException +
                ", errorCausingCommand=" + errorCausingCommand +
                ']';
    }

    @Override
    public void accept(final ResultVisitor<E> visitor) {
        visitor.onError(dittoRuntimeException, errorCausingCommand);
    }

    @Override
    public <F extends Event<?>> Result<F> map(final Function<E, F> mappingFunction) {
        return new ErrorResult<>(dittoRuntimeException, errorCausingCommand);
    }
}
