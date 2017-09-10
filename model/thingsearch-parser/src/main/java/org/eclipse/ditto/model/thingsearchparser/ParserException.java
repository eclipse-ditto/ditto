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
package org.eclipse.ditto.model.thingsearchparser;

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
