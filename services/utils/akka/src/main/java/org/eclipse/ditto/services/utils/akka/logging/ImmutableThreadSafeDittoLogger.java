/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * An immutable implementation of {@link ThreadSafeDittoLogger}.
 *
 * @since 1.3.0
 */
@Immutable
final class ImmutableThreadSafeDittoLogger implements ThreadSafeDittoLogger {

    private final Logger plainSlf4jLogger;
    @Nullable private final CharSequence correlationId;
    private final Logger loggerToUse;

    private ImmutableThreadSafeDittoLogger(final Logger plainSlf4jLogger, @Nullable final CharSequence correlationId) {
        this.plainSlf4jLogger = plainSlf4jLogger;
        this.correlationId = correlationId;
        if (isUsableCorrelationId(correlationId)) {
            loggerToUse = new LoggerWithCorrelationId();
        } else {
            loggerToUse = this.plainSlf4jLogger;
        }
    }

    private static boolean isUsableCorrelationId(@Nullable final CharSequence correlationId) {
        return null != correlationId && 0 < correlationId.length();
    }

    static ImmutableThreadSafeDittoLogger of(final Logger logger) {
        return new ImmutableThreadSafeDittoLogger(checkNotNull(logger, "logger"), null);
    }

    @Override
    public String getName() {
        return loggerToUse.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return loggerToUse.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        loggerToUse.trace(msg);
    }

    @Override
    public void trace(final String format, final Object arg) {
        loggerToUse.trace(format, arg);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        loggerToUse.trace(format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        loggerToUse.trace(format, arguments);
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        loggerToUse.trace(msg, t);
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return loggerToUse.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        loggerToUse.trace(marker, msg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        loggerToUse.trace(marker, format, arg);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        loggerToUse.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        loggerToUse.trace(marker, format, argArray);
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        loggerToUse.trace(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return loggerToUse.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        loggerToUse.debug(msg);
    }

    @Override
    public void debug(final String format, final Object arg) {
        loggerToUse.debug(format, arg);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        loggerToUse.debug(format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        loggerToUse.debug(format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        loggerToUse.debug(msg, t);
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return loggerToUse.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        loggerToUse.debug(marker, msg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        loggerToUse.debug(marker, format, arg);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        loggerToUse.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        loggerToUse.debug(marker, format, arguments);
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        loggerToUse.debug(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return loggerToUse.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        loggerToUse.info(msg);
    }

    @Override
    public void info(final String format, final Object arg) {
        loggerToUse.info(format, arg);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        loggerToUse.info(format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        loggerToUse.info(format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
        loggerToUse.info(msg, t);
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return loggerToUse.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        loggerToUse.info(marker, msg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        loggerToUse.info(marker, format, arg);
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        loggerToUse.info(marker, format, arg1, arg2);
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        loggerToUse.info(marker, format, arguments);
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        loggerToUse.info(marker, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return loggerToUse.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        loggerToUse.warn(msg);
    }

    @Override
    public void warn(final String format, final Object arg) {
        loggerToUse.warn(format, arg);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        loggerToUse.warn(format, arguments);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        loggerToUse.warn(format, arg1, arg2);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        loggerToUse.warn(msg, t);
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return loggerToUse.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        loggerToUse.warn(marker, msg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        loggerToUse.warn(marker, format, arg);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        loggerToUse.warn(marker, format, arg1, arg2);
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        loggerToUse.warn(marker, format, arguments);
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        loggerToUse.warn(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return loggerToUse.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        loggerToUse.error(msg);
    }

    @Override
    public void error(final String format, final Object arg) {
        loggerToUse.error(format, arg);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        loggerToUse.error(format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        loggerToUse.error(format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
        loggerToUse.error(msg, t);
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return loggerToUse.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        loggerToUse.error(marker, msg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        loggerToUse.error(marker, format, arg);
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        loggerToUse.error(marker, format, arg1, arg2);
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        loggerToUse.error(marker, format, arguments);
    }

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        loggerToUse.error(marker, msg, t);
    }

    @Override
    public ImmutableThreadSafeDittoLogger withCorrelationId(@Nullable final CharSequence correlationId) {
        final ImmutableThreadSafeDittoLogger result;
        if (Objects.equals(this.correlationId, correlationId)) {
            result = this;
        } else {
            result = new ImmutableThreadSafeDittoLogger(plainSlf4jLogger, correlationId);
        }
        return result;
    }

    @Override
    public ImmutableThreadSafeDittoLogger withCorrelationId(final WithDittoHeaders<?> withDittoHeaders) {
        return withCorrelationId(withDittoHeaders.getDittoHeaders());
    }

    @Override
    public ImmutableThreadSafeDittoLogger withCorrelationId(final DittoHeaders dittoHeaders) {
        return withCorrelationId(dittoHeaders.getCorrelationId().orElse(null));
    }

    @Override
    public ImmutableThreadSafeDittoLogger discardCorrelationId() {
        final ImmutableThreadSafeDittoLogger result;
        if (null == correlationId) {
            result = this;
        } else {
            removeCorrelationIdFromMdc();
            result = withCorrelationId((CharSequence) null);
        }
        return result;
    }

    private static void removeCorrelationIdFromMdc() {
        MDC.remove(LogUtil.X_CORRELATION_ID);
    }

    @SuppressWarnings("OverlyComplexClass") // Human beings can easily comprehend this class.
    @Immutable
    private final class LoggerWithCorrelationId implements Logger {

        private LoggerWithCorrelationId() {
            super();
        }

        @Override
        public String getName() {
            return plainSlf4jLogger.getName();
        }

        @Override
        public boolean isTraceEnabled() {
            return plainSlf4jLogger.isTraceEnabled();
        }

        @Override
        public void trace(final String msg) {
            if (isTraceEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        private void putCorrelationIdToMdc() {

            // At this point it is guaranteed that correlation ID is not null and not empty.
            // See constructor of surrounding class.
            MDC.put(LogUtil.X_CORRELATION_ID, String.valueOf(correlationId));
        }

        @Override
        public void trace(final String format, final Object arg) {
            if (isTraceEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2) {
            if (isTraceEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final String format, final Object... arguments) {
            if (isTraceEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final String msg, final Throwable t) {
            if (isTraceEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isTraceEnabled(final Marker marker) {
            return plainSlf4jLogger.isTraceEnabled(marker);
        }

        @Override
        public void trace(final Marker marker, final String msg) {
            if (isTraceEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(marker, msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object arg) {
            if (isTraceEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(marker, format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isTraceEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(marker, format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object... argArray) {
            if (isTraceEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(marker, format, argArray);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String msg, final Throwable t) {
            if (isTraceEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.trace(marker, msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return plainSlf4jLogger.isDebugEnabled();
        }

        @Override
        public void debug(final String msg) {
            if (isDebugEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object arg) {
            if (isDebugEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2) {
            if (isDebugEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object... arguments) {
            if (isDebugEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final String msg, final Throwable t) {
            if (isDebugEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isDebugEnabled(final Marker marker) {
            return plainSlf4jLogger.isDebugEnabled(marker);
        }

        @Override
        public void debug(final Marker marker, final String msg) {
            if (isDebugEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(marker, msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object arg) {
            if (isDebugEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(marker, format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isDebugEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(marker, format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object... arguments) {
            if (isDebugEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(marker, format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String msg, final Throwable t) {
            if (isDebugEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.debug(marker, msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isInfoEnabled() {
            return plainSlf4jLogger.isInfoEnabled();
        }

        @Override
        public void info(final String msg) {
            if (isInfoEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object arg) {
            if (isInfoEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2) {
            if (isInfoEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object... arguments) {
            if (isInfoEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final String msg, final Throwable t) {
            if (isInfoEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isInfoEnabled(final Marker marker) {
            return plainSlf4jLogger.isInfoEnabled(marker);
        }

        @Override
        public void info(final Marker marker, final String msg) {
            if (isInfoEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(marker, msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object arg) {
            if (isInfoEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(marker, format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isInfoEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(marker, format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object... arguments) {
            if (isInfoEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(marker, format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String msg, final Throwable t) {
            if (isInfoEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.info(marker, msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isWarnEnabled() {
            return plainSlf4jLogger.isWarnEnabled();
        }

        @Override
        public void warn(final String msg) {
            if (isWarnEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object arg) {
            if (isWarnEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object... arguments) {
            if (isWarnEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2) {
            if (isWarnEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final String msg, final Throwable t) {
            if (isWarnEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isWarnEnabled(final Marker marker) {
            return plainSlf4jLogger.isWarnEnabled(marker);
        }

        @Override
        public void warn(final Marker marker, final String msg) {
            if (isWarnEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(marker, msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object arg) {
            if (isWarnEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(marker, format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isWarnEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(marker, format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object... arguments) {
            if (isWarnEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(marker, format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String msg, final Throwable t) {
            if (isWarnEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.warn(marker, msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isErrorEnabled() {
            return plainSlf4jLogger.isErrorEnabled();
        }

        @Override
        public void error(final String msg) {
            if (isErrorEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object arg) {
            if (isErrorEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2) {
            if (isErrorEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object... arguments) {
            if (isErrorEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final String msg, final Throwable t) {
            if (isErrorEnabled()) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public boolean isErrorEnabled(final Marker marker) {
            return plainSlf4jLogger.isErrorEnabled(marker);
        }

        @Override
        public void error(final Marker marker, final String msg) {
            if (isErrorEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(marker, msg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object arg) {
            if (isErrorEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(marker, format, arg);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isErrorEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(marker, format, arg1, arg2);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object... arguments) {
            if (isErrorEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(marker, format, arguments);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String msg, final Throwable t) {
            if (isErrorEnabled(marker)) {
                try {
                    putCorrelationIdToMdc();
                    plainSlf4jLogger.error(marker, msg, t);
                } finally {
                    removeCorrelationIdFromMdc();
                }
            }
        }

    }

}
