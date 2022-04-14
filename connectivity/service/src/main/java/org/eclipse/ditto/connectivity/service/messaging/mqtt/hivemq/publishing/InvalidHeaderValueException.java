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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.Serial;
import java.text.MessageFormat;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * This exception is thrown to indicate that a user provided an invalid header value.
 */
final class InvalidHeaderValueException extends RuntimeException {

    @Serial private static final long serialVersionUID = -29105610818341810L;

    /**
     * Constructs a {@code InvalidMqttQosCodeException} for the specified invalid MQTT QoS code.
     *
     * @param headerName name of the header with an invalid value.
     * @param invalidityReason describes why the value is invalid.
     * @throws NullPointerException if any argument but {@code cause} is {@code null}.
     * @throws IllegalArgumentException if {@code headerName} or {@code invalidityReason} is empty or blank.
     */
    InvalidHeaderValueException(final String headerName, final String invalidityReason) {
        super(createDetailMessage(headerName, invalidityReason));
    }

    private static String createDetailMessage(final String headerName, final String invalidityReason) {
        checkArgumentNotBlank(headerName, "headerName");
        checkArgumentNotBlank(invalidityReason, "invalidityReason");
        return MessageFormat.format("Invalid value for header <{0}>: {1}", headerName, invalidityReason);
    }

    private static void checkArgumentNotBlank(final String argument, final String argumentName) {
        ConditionChecker.checkArgument(checkNotNull(argument, argumentName),
                arg -> !arg.isBlank(),
                () -> MessageFormat.format("The argument {0} must not be blank.", argumentName));
    }

    /**
     * Constructs a {@code InvalidMqttQosCodeException} for the specified invalid MQTT QoS code.
     *
     * @param headerName name of the header with an invalid value.
     * @param cause the cause for the exception or {@code null}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code headerName} is empty or blank.
     */
    InvalidHeaderValueException(final String headerName, final Throwable cause) {
        super(createDetailMessage(headerName, checkNotNull(cause, "cause").getMessage()), cause);
    }

}
