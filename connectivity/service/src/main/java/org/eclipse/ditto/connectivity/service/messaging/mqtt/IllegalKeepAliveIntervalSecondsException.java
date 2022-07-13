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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.io.Serial;

import javax.annotation.Nullable;

/**
 * This exception is thrown to indicate that the seconds of an MQTT keep alive interval is outside its allowed range.
 */
public final class IllegalKeepAliveIntervalSecondsException extends Exception {

    @Serial private static final long serialVersionUID = 1749375435178271254L;

    /**
     * Constructs a {@code IllegalKeepAliveIntervalSecondsException} for the specified detail message argument.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the cause of the exception or {@code null} if unknown.
     */
    IllegalKeepAliveIntervalSecondsException(final String detailMessage, @Nullable final Throwable cause) {
        super(detailMessage, cause);
    }

}
