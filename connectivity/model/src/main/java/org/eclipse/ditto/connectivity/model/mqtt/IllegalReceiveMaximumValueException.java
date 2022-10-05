/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.mqtt;

import javax.annotation.Nullable;

/**
 * This exception is thrown to indicate that a particular number value does not represent a valid MQTT Receive Maximum.
 * The details that led to this exception are stated in the detail message.
 */
public final class IllegalReceiveMaximumValueException extends Exception {

    private static final long serialVersionUID = -2401780934628723671L;

    /**
     * Constructs an {@code IllegalReceiveMaximumValueException} for the specified detail message argument.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the cause of the exception or {@code null} if unknown.
     */
    IllegalReceiveMaximumValueException(final String detailMessage, final @Nullable Throwable cause) {
        super(detailMessage, cause);
    }

}
