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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * An immutable implementation of {@link ThreadSafeDittoLogger}.
 *
 * @since 1.4.0
 */
@Immutable
final class ImmutableDittoLogger implements ThreadSafeDittoLogger {

    private final Logger plainSlf4jLogger;
    private final Map<String, String> localMdc;
    private final Logger loggerToUse;

    private ImmutableDittoLogger(final Logger plainSlf4jLogger, final Map<String, String> localMdc) {
        this.plainSlf4jLogger = plainSlf4jLogger;
        this.localMdc = localMdc;
        if (localMdc.isEmpty()) {
            loggerToUse = this.plainSlf4jLogger;
        } else {
            loggerToUse = new MdcUsingLogger();
        }
    }

    static ImmutableDittoLogger of(final Logger logger) {
        return new ImmutableDittoLogger(checkNotNull(logger, "logger"), new HashMap<>(5));
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
    public ImmutableDittoLogger withCorrelationId(@Nullable final CharSequence correlationId) {
        return withMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
    }

    @Override
    public ImmutableDittoLogger withCorrelationId(@Nullable final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(null != withDittoHeaders ? withDittoHeaders.getDittoHeaders() : null);
    }

    @Override
    public ImmutableDittoLogger withCorrelationId(@Nullable final DittoHeaders dittoHeaders) {
        return withCorrelationId(null != dittoHeaders ? dittoHeaders.getCorrelationId().orElse(null) : null);
    }

    @Override
    public ImmutableDittoLogger discardCorrelationId() {
        return removeMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public ImmutableDittoLogger withMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        validateMdcEntryKey(key, "key");
        @Nullable final CharSequence existingValue = localMdc.get(key.toString());
        final ImmutableDittoLogger result;
        if (null != value) {
            if (value.equals(existingValue)) {
                result = this;
            } else {
                final Map<String, String> newLocalMdc = copyLocalMdc();
                newLocalMdc.put(key.toString(), value.toString());
                result = new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
            }
        } else {
            if (null == existingValue) {
                result = this;
            } else {
                final Map<String, String> newLocalMdc = copyLocalMdc();
                newLocalMdc.remove(key.toString());
                result = new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
            }
        }
        return result;
    }

    private static void validateMdcEntryKey(final CharSequence key, final String argumentName) {
        argumentNotEmpty(key, argumentName);
    }

    private Map<String, String> copyLocalMdc() {
        return new HashMap<>(localMdc);
    }

    @Override
    public ImmutableDittoLogger withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");

        final Map<String, String> newLocalMdc = copyLocalMdc();
        putOrRemove(k1, v1, newLocalMdc);
        putOrRemove(k2, v2, newLocalMdc);

        return new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
    }

    private static void putOrRemove(final CharSequence key, @Nullable final CharSequence value,
            final Map<String, String> map) {

        if (null == value) {
            map.remove(key.toString());
        } else {
            map.put(key.toString(), value.toString());
        }
    }

    @Override
    public ImmutableDittoLogger withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");
        validateMdcEntryKey(k3, "k3");

        final Map<String, String> newLocalMdc = copyLocalMdc();
        putOrRemove(k1, v1, newLocalMdc);
        putOrRemove(k2, v2, newLocalMdc);
        putOrRemove(k3, v3, newLocalMdc);

        return new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
    }

    @Override
    public ImmutableDittoLogger withMdcEntry(final MdcEntry mdcEntry, final MdcEntry... furtherMdcEntries) {
        checkNotNull(mdcEntry, "mdcEntry");
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        final Map<String, String> newLocalMdc = copyLocalMdc();
        newLocalMdc.put(mdcEntry.getKey(), mdcEntry.getValueOrNull());

        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            newLocalMdc.put(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }

        return new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
    }

    @Override
    public ImmutableDittoLogger removeMdcEntry(final CharSequence key) {
        validateMdcEntryKey(key, "key");
        try {
            final ImmutableDittoLogger result;
            if (localMdc.containsKey(key.toString())) {
                final Map<String, String> newLocalMdc = copyLocalMdc();
                newLocalMdc.remove(key.toString());
                result = new ImmutableDittoLogger(plainSlf4jLogger, newLocalMdc);
            } else {
                result = this;
            }
            return result;
        } finally {
            MDC.remove(key.toString());
        }
    }

    @SuppressWarnings("OverlyComplexClass") // Human beings can easily comprehend this class.
    @Immutable
    private final class MdcUsingLogger implements Logger {

        private MdcUsingLogger() {
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        private void putLocalMdcToActualMdc() {
            localMdc.forEach(MDC::put);
        }

        private void removeLocalMdcFromActualMdc() {
            localMdc.forEach((key, value) -> MDC.remove(key));
        }

        @Override
        public void trace(final String format, final Object arg) {
            if (isTraceEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2) {
            if (isTraceEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final String format, final Object... arguments) {
            if (isTraceEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final String msg, final Throwable t) {
            if (isTraceEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(marker, msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object arg) {
            if (isTraceEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(marker, format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isTraceEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(marker, format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String format, final Object... argArray) {
            if (isTraceEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(marker, format, argArray);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void trace(final Marker marker, final String msg, final Throwable t) {
            if (isTraceEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.trace(marker, msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object arg) {
            if (isDebugEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2) {
            if (isDebugEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final String format, final Object... arguments) {
            if (isDebugEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final String msg, final Throwable t) {
            if (isDebugEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(marker, msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object arg) {
            if (isDebugEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(marker, format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isDebugEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(marker, format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String format, final Object... arguments) {
            if (isDebugEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(marker, format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void debug(final Marker marker, final String msg, final Throwable t) {
            if (isDebugEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.debug(marker, msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object arg) {
            if (isInfoEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2) {
            if (isInfoEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final String format, final Object... arguments) {
            if (isInfoEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final String msg, final Throwable t) {
            if (isInfoEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(marker, msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object arg) {
            if (isInfoEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(marker, format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isInfoEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(marker, format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String format, final Object... arguments) {
            if (isInfoEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(marker, format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void info(final Marker marker, final String msg, final Throwable t) {
            if (isInfoEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.info(marker, msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object arg) {
            if (isWarnEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object... arguments) {
            if (isWarnEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2) {
            if (isWarnEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final String msg, final Throwable t) {
            if (isWarnEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(marker, msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object arg) {
            if (isWarnEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(marker, format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isWarnEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(marker, format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String format, final Object... arguments) {
            if (isWarnEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(marker, format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void warn(final Marker marker, final String msg, final Throwable t) {
            if (isWarnEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.warn(marker, msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object arg) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final String format, final Object... arguments) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final String msg, final Throwable t) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
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
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(marker, msg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object arg) {
            if (isErrorEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(marker, format, arg);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
            if (isErrorEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(marker, format, arg1, arg2);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String format, final Object... arguments) {
            if (isErrorEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(marker, format, arguments);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void error(final Marker marker, final String msg, final Throwable t) {
            if (isErrorEnabled(marker)) {
                try {
                    putLocalMdcToActualMdc();
                    plainSlf4jLogger.error(marker, msg, t);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

    }

}
