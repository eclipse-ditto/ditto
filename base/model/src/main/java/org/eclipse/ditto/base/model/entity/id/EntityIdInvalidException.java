/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import java.net.URI;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Abstract base class for all exceptions that indicate an invalid entity ID.
 *
 * @since 2.1.0
 */
public abstract class EntityIdInvalidException extends DittoRuntimeException {

    private static final long serialVersionUID = -1231422633844787229L;

    /**
     * Constructs a new {@code EntityIdInvalidException} object.
     * The HTTP status is set to {@link HttpStatus#BAD_REQUEST}.
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode} or {@code dittoHeaders} is {@code null}.
     */
    protected EntityIdInvalidException(final String errorCode,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(errorCode, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

}
