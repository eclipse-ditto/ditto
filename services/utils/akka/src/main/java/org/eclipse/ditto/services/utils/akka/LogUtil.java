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
package org.eclipse.ditto.services.utils.akka;

import java.util.Optional;

import org.slf4j.MDC;

import akka.actor.Actor;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;

/**
 * Utilities for logging.
 */
public final class LogUtil {

    /**
     * Name of the Header for the global Ditto correlation ID.
     */
    public static final String X_CORRELATION_ID = "x-correlation-id";

    /*
     * Inhibit instantiation of this utility class.
     */
    private LogUtil() {
        throw new AssertionError();
    }

    /**
     * Obtain LoggingAdapter with MDC support for the given actor.
     *
     * @param logSource the Actor used as logSource
     * @return the created DiagnosticLoggingAdapter.
     */
    public static DiagnosticLoggingAdapter obtain(final Actor logSource) {
        return Logging.apply(logSource);
    }

    /**
     * Gets the {@code correlationId} from the default slf4j {@link org.slf4j.MDC}.
     *
     * @return the {@code correlationId} from {@link org.slf4j.MDC} or an empty {@link java.util.Optional} if it didn't exist.
     */
    public static Optional<String> getCorrelationId() {
        final String correlationId = MDC.get(X_CORRELATION_ID);
        if (null != correlationId) {
            return Optional.of(correlationId);
        }
        return Optional.empty();
    }

}
