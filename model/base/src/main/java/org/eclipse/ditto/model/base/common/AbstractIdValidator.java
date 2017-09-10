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
package org.eclipse.ditto.model.base.common;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Abstract implementation for validation of identifiers.
 */
public abstract class AbstractIdValidator implements BiConsumer<CharSequence, DittoHeaders> {

    private final String idRegex;

    /**
     * Constructs a new {@code AbstractIdValidator} object.
     *
     * @param idRegex the regular expression to match IDs to.
     */
    protected AbstractIdValidator(final String idRegex) {
        this.idRegex = idRegex;
    }

    /**
     * Validates a given {@code id}.
     *
     * @param id the ID.
     * @param dittoHeaders the headers that provide a correlation ID.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if {@code id} is invalid.
     */
    @Override
    public void accept(@Nullable final CharSequence id, final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "Ditto Headers");
        final IdValidator idValidator = IdValidator.newInstance(id, idRegex);
        if (!idValidator.isValid()) {
            final DittoRuntimeExceptionBuilder builder = createBuilder(id).dittoHeaders(dittoHeaders);
            idValidator.getReason().ifPresent(builder::message);
            throw builder.build();
        }
    }

    protected abstract DittoRuntimeExceptionBuilder createBuilder(@Nullable CharSequence id);

}
