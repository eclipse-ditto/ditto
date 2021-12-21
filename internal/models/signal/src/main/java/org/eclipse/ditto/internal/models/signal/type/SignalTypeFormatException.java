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
package org.eclipse.ditto.internal.models.signal.type;

/**
 * Thrown to indicate that the application has attempted to convert a CharSequence to a signal type, but that
 * CharSequence does not have the appropriate format.
 *
 * @since 2.3.0
 */
public final class SignalTypeFormatException extends Exception {

    private static final long serialVersionUID = 3943293990295685963L;

    /**
     * Constructs a {@code SignalTypeFormatException} with the specified detail message.
     *
     * @param message the detail message.
     */
    public SignalTypeFormatException(final String message) {
        super(message);
    }

}
