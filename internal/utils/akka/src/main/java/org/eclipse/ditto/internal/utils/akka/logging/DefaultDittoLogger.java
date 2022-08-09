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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Default implementation of {@link DittoLogger}.
 * It harnesses the state pattern for determining how long a particular correlation ID is valid.
 */
@NotThreadSafe
final class DefaultDittoLogger implements DittoLogger, AutoCloseableSlf4jLogger {

    private final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger;
    private final AutoCloseableSlf4jLogger autoClosingSlf4jLogger;
    private AutoCloseableSlf4jLogger currentLogger;

    private DefaultDittoLogger(final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger,
            final AutoCloseableSlf4jLogger autoClosingSlf4jLogger) {

        this.autoCloseableSlf4jLogger = autoCloseableSlf4jLogger;
        this.autoClosingSlf4jLogger = autoClosingSlf4jLogger;
        currentLogger = this.autoClosingSlf4jLogger;
    }

    /**
     * Returns an instance of the default Ditto logger.
     *
     * @param logger the SLF4J logger which performs the actual logging.
     * @return the default Ditto logger.
     * @throws NullPointerException if {@code logger} is {@code null}.
     */
    public static DefaultDittoLogger of(final Logger logger) {
        final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger = DefaultAutoCloseableSlf4jLogger.of(logger);
        return new DefaultDittoLogger(autoCloseableSlf4jLogger, AutoClosingSlf4jLogger.of(autoCloseableSlf4jLogger));
    }

    @Override
    public DefaultDittoLogger withCorrelationId(@Nullable final CharSequence correlationId) {
        currentLogger = autoClosingSlf4jLogger;
        currentLogger.setCorrelationId(correlationId);
        return this;
    }

    @Override
    public DefaultDittoLogger withCorrelationId(@Nullable final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(null != withDittoHeaders ? withDittoHeaders.getDittoHeaders() : null);
    }

    @Override
    public DefaultDittoLogger withCorrelationId(@Nullable final DittoHeaders dittoHeaders) {
        return withCorrelationId(null != dittoHeaders ? dittoHeaders.getCorrelationId().orElse(null) : null);
    }

    @Override
    public DefaultDittoLogger setCorrelationId(@Nullable final CharSequence correlationId) {
        currentLogger = autoCloseableSlf4jLogger;
        currentLogger.setCorrelationId(correlationId);
        return this;
    }

    @Override
    public DefaultDittoLogger setCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return setCorrelationId(checkNotNull(withDittoHeaders, "withDittoHeaders").getDittoHeaders());
    }

    @Override
    public DefaultDittoLogger setCorrelationId(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        return setCorrelationId(dittoHeaders.getCorrelationId().orElse(null));
    }

    @Override
    public void discardCorrelationId() {
        currentLogger.discardCorrelationId();
    }

    @Override
    public AutoCloseableSlf4jLogger putMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        currentLogger = autoCloseableSlf4jLogger;
        currentLogger.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoLogger withMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        currentLogger = autoClosingSlf4jLogger;
        currentLogger.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoLogger withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        currentLogger = autoClosingSlf4jLogger;
        currentLogger.putMdcEntry(k1, v1);
        currentLogger.putMdcEntry(k2, v2);
        return this;
    }

    @Override
    public DefaultDittoLogger withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        currentLogger = autoClosingSlf4jLogger;
        currentLogger.putMdcEntry(k1, v1);
        currentLogger.putMdcEntry(k2, v2);
        currentLogger.putMdcEntry(k3, v3);
        return this;
    }

    @Override
    public DefaultDittoLogger withMdcEntry(final MdcEntry mdcEntry, final MdcEntry... furtherMdcEntries) {
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        currentLogger = autoClosingSlf4jLogger;
        currentLogger.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            currentLogger.putMdcEntry(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }
        return this;
    }

    @Override
    public DefaultDittoLogger removeMdcEntry(final CharSequence key) {
        currentLogger.removeMdcEntry(key);
        return this;
    }

    @Override
    public String getName() {
        return currentLogger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return currentLogger.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        currentLogger.trace(msg);
    }

    @Override
    public void trace(final String format, final Object arg) {
        currentLogger.trace(format, arg);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        currentLogger.trace(format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        currentLogger.trace(format, arguments);
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        currentLogger.trace(msg, t);
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return currentLogger.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        currentLogger.trace(marker, msg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        currentLogger.trace(marker, format, arg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        currentLogger.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        currentLogger.trace(marker, format, argArray);
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        currentLogger.trace(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return currentLogger.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        currentLogger.debug(msg);
    }

    @Override
    public void debug(final String format, final Object arg) {
        currentLogger.debug(format, arg);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        currentLogger.debug(format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        currentLogger.debug(format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        currentLogger.debug(msg, t);
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return currentLogger.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        currentLogger.debug(marker, msg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        currentLogger.debug(marker, format, arg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        currentLogger.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        currentLogger.debug(marker, format, arguments);
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        currentLogger.debug(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return currentLogger.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        currentLogger.info(msg);
    }

    @Override
    public void info(final String format, final Object arg) {
        currentLogger.info(format, arg);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        currentLogger.info(format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        currentLogger.info(format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
        currentLogger.info(msg, t);
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return currentLogger.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        currentLogger.info(marker, msg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        currentLogger.info(marker, format, arg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        currentLogger.info(marker, format, arg1, arg2);
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        currentLogger.info(marker, format, arguments);
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        currentLogger.info(marker, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return currentLogger.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        currentLogger.warn(msg);
    }

    @Override
    public void warn(final String format, final Object arg) {
        currentLogger.warn(format, arg);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        currentLogger.warn(format, arguments);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        currentLogger.warn(format, arg1, arg2);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        currentLogger.warn(msg, t);
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return currentLogger.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        currentLogger.warn(marker, msg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        currentLogger.warn(marker, format, arg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        currentLogger.warn(marker, format, arg1, arg2);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        currentLogger.warn(marker, format, arguments);
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        currentLogger.warn(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return currentLogger.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        currentLogger.error(msg);
    }

    @Override
    public void error(final String format, final Object arg) {
        currentLogger.error(format, arg);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        currentLogger.error(format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        currentLogger.error(format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
        currentLogger.error(msg, t);
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return currentLogger.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        currentLogger.error(marker, msg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        currentLogger.error(marker, format, arg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        currentLogger.error(marker, format, arg1, arg2);
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        currentLogger.error(marker, format, arguments);
    }

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        currentLogger.error(marker, msg, t);
    }

    @Override
    public void close() {
        currentLogger.close();
        currentLogger = autoClosingSlf4jLogger;
    }

}
