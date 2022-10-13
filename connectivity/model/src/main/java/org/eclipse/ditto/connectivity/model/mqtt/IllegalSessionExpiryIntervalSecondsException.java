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
 * This exception is thrown to indicate that the seconds of an MQTT
 * session expiry interval is outside its allowed range.
 */
public final class IllegalSessionExpiryIntervalSecondsException extends Exception {

    private static final long serialVersionUID = -5041122338251813443L;

    /**
     * Constructs a {@code IllegalSessionExpiryIntervalSecondsException} for the specified detail message argument.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the cause of the exception or {@code null} if unknown.
     */
    IllegalSessionExpiryIntervalSecondsException(final String detailMessage, @Nullable final Throwable cause) {
        super(detailMessage, cause);
    }

}
