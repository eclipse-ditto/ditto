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
package org.eclipse.ditto.services.utils.persistentactors.results;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Result signifying an error.
 */
public final class ErrorResult<E> implements Result<E> {

    private final DittoRuntimeException dittoRuntimeException;

    ErrorResult(final DittoRuntimeException dittoRuntimeException) {
        this.dittoRuntimeException = dittoRuntimeException;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "dittoRuntimeException=" + dittoRuntimeException +
                ']';
    }

    @Override
    public void accept(final ResultVisitor<E> visitor) {
        visitor.onError(dittoRuntimeException);
    }
}
