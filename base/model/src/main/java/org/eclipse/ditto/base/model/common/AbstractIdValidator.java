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
package org.eclipse.ditto.base.model.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
        this.idRegex = checkNotNull(idRegex, "idRegex");
    }

    /**
     * Validates a given {@code id}.
     *
     * @param id the ID.
     * @param dittoHeaders the headers that provide a correlation ID.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if {@code id} is invalid.
     */
    @Override
    public void accept(@Nullable final CharSequence id, final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "Ditto Headers");
        final IdValidator idValidator = IdValidator.newInstance(id, idRegex);
        if (!idValidator.isValid()) {
            final DittoRuntimeExceptionBuilder<?> builder = createExceptionBuilder(id).dittoHeaders(dittoHeaders);
            idValidator.getReason().ifPresent(builder::message);
            throw builder.build();
        }
    }

    protected abstract DittoRuntimeExceptionBuilder<?> createExceptionBuilder(@Nullable CharSequence id);

}
