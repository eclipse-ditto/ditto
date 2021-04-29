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
package org.eclipse.ditto.rql.model;

/**
 * Exception that can be thrown by a parser if there occurred errors during the parsing of the input.
 */
public class ParserException extends RuntimeException {

    private static final long serialVersionUID = -6383738370508177550L;

    /**
     * Constructs a new predicate parse exception without detail message or cause.
     */
    public ParserException() {
        super();
    }

    /**
     * Constructs a new predicate parse exception.
     *
     * @param message the detail message.
     */
    public ParserException(final String message) {
        super(message);
    }

    /**
     * Constructs a new predicate parse exception with the specified cause.
     *
     * @param cause the cause.
     */
    public ParserException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new predicate parse exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public ParserException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
