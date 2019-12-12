/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.logging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * This implementation of {@link AutoCloseableSlf4jLogger} wraps and delegates to a {@link Logger}.
 * Furthermore it provides the means to discard the correlation ID.
 */
@NotThreadSafe
final class DefaultAutoCloseableSlf4jLogger implements AutoCloseableSlf4jLogger {

    private final Logger actualSlf4jLogger;

    private DefaultAutoCloseableSlf4jLogger(final Logger actualSlf4jLogger) {
        this.actualSlf4jLogger = actualSlf4jLogger;
    }

    /**
     * Returns an instance of an auto-closeable SLF4J logger.
     *
     * @param logger the actual SLF4J logger to delegate all logging to.
     * @return the instance
     * @throws NullPointerException if {@code logger} is {@code null}.
     */
    public static DefaultAutoCloseableSlf4jLogger of(final Logger logger) {
        return new DefaultAutoCloseableSlf4jLogger(checkNotNull(logger, "logger"));
    }

    @Override
    public String getName() {
        return actualSlf4jLogger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return actualSlf4jLogger.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        actualSlf4jLogger.trace(msg);
    }

    @Override
    public void trace(final String format, final Object arg) {
        actualSlf4jLogger.trace(format, arg);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.trace(format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        actualSlf4jLogger.trace(format, arguments);
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        actualSlf4jLogger.trace(msg, t);
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return actualSlf4jLogger.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        actualSlf4jLogger.trace(marker, msg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        actualSlf4jLogger.trace(marker, format, arg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        actualSlf4jLogger.trace(marker, format, argArray);
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        actualSlf4jLogger.trace(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return actualSlf4jLogger.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        actualSlf4jLogger.debug(msg);
    }

    @Override
    public void debug(final String format, final Object arg) {
        actualSlf4jLogger.debug(format, arg);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.debug(format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        actualSlf4jLogger.debug(format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        actualSlf4jLogger.debug(msg, t);
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return actualSlf4jLogger.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        actualSlf4jLogger.debug(marker, msg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        actualSlf4jLogger.debug(marker, format, arg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        actualSlf4jLogger.debug(marker, format, arguments);
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        actualSlf4jLogger.debug(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return actualSlf4jLogger.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        actualSlf4jLogger.info(msg);
    }

    @Override
    public void info(final String format, final Object arg) {
        actualSlf4jLogger.info(format, arg);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.info(format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        actualSlf4jLogger.info(format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
        actualSlf4jLogger.info(msg, t);
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return actualSlf4jLogger.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        actualSlf4jLogger.info(marker, msg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        actualSlf4jLogger.info(marker, format, arg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.info(marker, format, arg1, arg2);
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        actualSlf4jLogger.info(marker, format, arguments);
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        actualSlf4jLogger.info(marker, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return actualSlf4jLogger.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        actualSlf4jLogger.warn(msg);
    }

    @Override
    public void warn(final String format, final Object arg) {
        actualSlf4jLogger.warn(format, arg);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        actualSlf4jLogger.warn(format, arguments);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.warn(format, arg1, arg2);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        actualSlf4jLogger.warn(msg, t);
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return actualSlf4jLogger.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        actualSlf4jLogger.warn(marker, msg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        actualSlf4jLogger.warn(marker, format, arg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.warn(marker, format, arg1, arg2);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        actualSlf4jLogger.warn(marker, format, arguments);
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        actualSlf4jLogger.warn(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return actualSlf4jLogger.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        actualSlf4jLogger.error(msg);
    }

    @Override
    public void error(final String format, final Object arg) {
        actualSlf4jLogger.error(format, arg);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.error(format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        actualSlf4jLogger.error(format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
        actualSlf4jLogger.error(msg, t);
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return actualSlf4jLogger.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        actualSlf4jLogger.error(marker, msg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        actualSlf4jLogger.error(marker, format, arg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        actualSlf4jLogger.error(marker, format, arg1, arg2);
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        actualSlf4jLogger.error(marker, format, arguments);
    }

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        actualSlf4jLogger.error(marker, msg, t);
    }

    @Override
    public void close() {
        LogUtil.removeCorrelationId();
    }

}
