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
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Aggregates all error responses relating to a given {@link Command}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ErrorResponse<T extends ErrorResponse> extends CommandResponse<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Returns the wrapped {@link DittoRuntimeException}.
     *
     * @return the wrapped exception.
     */
    DittoRuntimeException getDittoRuntimeException();
}
