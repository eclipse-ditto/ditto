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

import org.slf4j.Marker;

/**
 * This implementation of {@link AutoCloseableSlf4jLogger} discards all diagnostic values automatically after each log
 * operation.
 */
@NotThreadSafe
final class AutoClosingSlf4jLogger implements AutoCloseableSlf4jLogger {

    private final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger;

    private AutoClosingSlf4jLogger(final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger) {
        this.autoCloseableSlf4jLogger = autoCloseableSlf4jLogger;
    }

    public static AutoClosingSlf4jLogger of(final AutoCloseableSlf4jLogger autoCloseableSlf4jLogger) {
        return new AutoClosingSlf4jLogger(checkNotNull(autoCloseableSlf4jLogger, "autoCloseableSlf4jLogger"));
    }

    @Override
    public AutoClosingSlf4jLogger setCorrelationId(@Nullable final CharSequence correlationId) {
        autoCloseableSlf4jLogger.setCorrelationId(correlationId);
        return this;
    }

    @Override
    public void discardCorrelationId() {
        autoCloseableSlf4jLogger.discardCorrelationId();
    }

    @Override
    public AutoCloseableSlf4jLogger putMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        autoCloseableSlf4jLogger.putMdcEntry(key, value);
        return this;
    }

    @Override
    public AutoCloseableSlf4jLogger removeMdcEntry(final CharSequence key) {
        autoCloseableSlf4jLogger.removeMdcEntry(key);
        return this;
    }

    @Override
    public void close() {
        autoCloseableSlf4jLogger.close();
    }

    @Override
    public String getName() {
        return autoCloseableSlf4jLogger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return autoCloseableSlf4jLogger.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(msg);
        }
    }

    @Override
    public void trace(final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(format, arg);
        }
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(format, arg1, arg2);
        }
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(format, arguments);
        }
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(msg, t);
        }
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return autoCloseableSlf4jLogger.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(marker, msg);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(marker, format, arg);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(marker, format, arg1, arg2);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(marker, format, argArray);
        }
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.trace(marker, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return autoCloseableSlf4jLogger.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(msg);
        }
    }

    @Override
    public void debug(final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(format, arg);
        }
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(format, arg1, arg2);
        }
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(format, arguments);
        }
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return autoCloseableSlf4jLogger.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(marker, msg);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(marker, format, arg);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(marker, format, arg1, arg2);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(marker, format, arguments);
        }
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.debug(marker, msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return autoCloseableSlf4jLogger.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(msg);
        }
    }

    @Override
    public void info(final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(format, arg);
        }
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(format, arg1, arg2);
        }
    }

    @Override
    public void info(final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(format, arguments);
        }
    }

    @Override
    public void info(final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return autoCloseableSlf4jLogger.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(marker, msg);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(marker, format, arg);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(marker, format, arg1, arg2);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(marker, format, arguments);
        }
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.info(marker, msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return autoCloseableSlf4jLogger.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(msg);
        }
    }

    @Override
    public void warn(final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(format, arg);
        }
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(format, arguments);
        }
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(format, arg1, arg2);
        }
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return autoCloseableSlf4jLogger.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(marker, msg);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(marker, format, arg);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(marker, format, arg1, arg2);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(marker, format, arguments);
        }
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.warn(marker, msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return autoCloseableSlf4jLogger.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(msg);
        }
    }

    @Override
    public void error(final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(format, arg);
        }
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(format, arg1, arg2);
        }
    }

    @Override
    public void error(final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(format, arguments);
        }
    }

    @Override
    public void error(final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return autoCloseableSlf4jLogger.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(marker, msg);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(marker, format, arg);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(marker, format, arg1, arg2);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(marker, format, arguments);
        }
    }

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        try (final AutoCloseableSlf4jLogger l = autoCloseableSlf4jLogger) {
            l.error(marker, msg, t);
        }
    }

}
