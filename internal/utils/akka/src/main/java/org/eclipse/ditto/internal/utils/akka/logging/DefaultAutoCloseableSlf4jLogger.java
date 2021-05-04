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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * Default implementation of {@link AutoCloseableSlf4jLogger}.
 */
@NotThreadSafe
final class DefaultAutoCloseableSlf4jLogger implements AutoCloseableSlf4jLogger {

    private final Logger actualSlf4jLogger;
    private final Map<String, String> localMdc;

    private DefaultAutoCloseableSlf4jLogger(final Logger actualSlf4jLogger) {
        this.actualSlf4jLogger = actualSlf4jLogger;
        localMdc = new HashMap<>(5);
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
        if (isTraceEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(msg);
        }
    }

    private void tryToPutLocalMdcToActualMdc() {
        try {
            putLocalMdcToActualMdc();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private void putLocalMdcToActualMdc() {
        localMdc.forEach(MDC::put);
    }

    private void handleConcurrentModificationException() {

        // Logging should not interfere with application's actual work.
        actualSlf4jLogger.warn("This logger <{}> is used by multiple threads!" +
                " Please consider to use a thread-safe logger instead.", getName());
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (isTraceEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(format, arg);
        }
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (isTraceEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(format, arg1, arg2);
        }
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (isTraceEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(format, arguments);
        }
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        if (isTraceEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(msg, t);
        }
    }

    @Override
    public boolean isTraceEnabled(final Marker marker) {
        return actualSlf4jLogger.isTraceEnabled(marker);
    }

    @Override
    public void trace(final Marker marker, final String msg) {
        if (isTraceEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(marker, msg);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg) {
        if (isTraceEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(marker, format, arg);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (isTraceEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(marker, format, arg1, arg2);
        }
    }

    @Override
    public void trace(final Marker marker, final String format, final Object... argArray) {
        if (isTraceEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(marker, format, argArray);
        }
    }

    @Override
    public void trace(final Marker marker, final String msg, final Throwable t) {
        if (isTraceEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.trace(marker, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return actualSlf4jLogger.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        if (isDebugEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(msg);
        }
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (isDebugEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(format, arg);
        }
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(format, arg1, arg2);
        }
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (isDebugEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(format, arguments);
        }
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        if (isDebugEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled(final Marker marker) {
        return actualSlf4jLogger.isDebugEnabled(marker);
    }

    @Override
    public void debug(final Marker marker, final String msg) {
        if (isDebugEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(marker, msg);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg) {
        if (isDebugEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(marker, format, arg);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(marker, format, arg1, arg2);
        }
    }

    @Override
    public void debug(final Marker marker, final String format, final Object... arguments) {
        if (isDebugEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(marker, format, arguments);
        }
    }

    @Override
    public void debug(final Marker marker, final String msg, final Throwable t) {
        if (isDebugEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.debug(marker, msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return actualSlf4jLogger.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        if (isInfoEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(msg);
        }
    }

    @Override
    public void info(final String format, final Object arg) {
        if (isInfoEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(format, arg);
        }
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (isInfoEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(format, arg1, arg2);
        }
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (isInfoEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(format, arguments);
        }
    }

    @Override
    public void info(final String msg, final Throwable t) {
        if (isInfoEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled(final Marker marker) {
        return actualSlf4jLogger.isInfoEnabled(marker);
    }

    @Override
    public void info(final Marker marker, final String msg) {
        if (isInfoEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(marker, msg);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg) {
        if (isInfoEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(marker, format, arg);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (isInfoEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(marker, format, arg1, arg2);
        }
    }

    @Override
    public void info(final Marker marker, final String format, final Object... arguments) {
        if (isInfoEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(marker, format, arguments);
        }
    }

    @Override
    public void info(final Marker marker, final String msg, final Throwable t) {
        if (isInfoEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.info(marker, msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return actualSlf4jLogger.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        if (isWarnEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(msg);
        }
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (isWarnEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(format, arg);
        }
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (isWarnEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(format, arguments);
        }
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (isWarnEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(format, arg1, arg2);
        }
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        if (isWarnEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled(final Marker marker) {
        return actualSlf4jLogger.isWarnEnabled(marker);
    }

    @Override
    public void warn(final Marker marker, final String msg) {
        if (isWarnEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(marker, msg);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg) {
        if (isWarnEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(marker, format, arg);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (isWarnEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(marker, format, arg1, arg2);
        }
    }

    @Override
    public void warn(final Marker marker, final String format, final Object... arguments) {
        if (isWarnEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(marker, format, arguments);
        }
    }

    @Override
    public void warn(final Marker marker, final String msg, final Throwable t) {
        if (isWarnEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.warn(marker, msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return actualSlf4jLogger.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        if (isErrorEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(msg);
        }
    }

    @Override
    public void error(final String format, final Object arg) {
        if (isErrorEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(format, arg);
        }
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (isErrorEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(format, arg1, arg2);
        }
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (isErrorEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(format, arguments);
        }
    }

    @Override
    public void error(final String msg, final Throwable t) {
        if (isErrorEnabled()) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled(final Marker marker) {
        return actualSlf4jLogger.isErrorEnabled(marker);
    }

    @Override
    public void error(final Marker marker, final String msg) {
        if (isErrorEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(marker, msg);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg) {
        if (isErrorEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(marker, format, arg);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        if (isErrorEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(marker, format, arg1, arg2);
        }
    }

    @Override
    public void error(final Marker marker, final String format, final Object... arguments) {
        if (isErrorEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(marker, format, arguments);
        }
    }

    @Override
    public void error(final Marker marker, final String msg, final Throwable t) {
        if (isErrorEnabled(marker)) {
            tryToPutLocalMdcToActualMdc();
            actualSlf4jLogger.error(marker, msg, t);
        }
    }

    @Override
    public DefaultAutoCloseableSlf4jLogger setCorrelationId(@Nullable final CharSequence correlationId) {
        return putMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
    }

    @Override
    public void discardCorrelationId() {
        removeMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public DefaultAutoCloseableSlf4jLogger putMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        if (null != value) {
            tryToPutToLocalMdc(validateMdcEntryKey(key).toString(), value.toString());
        } else {
            removeMdcEntry(key);
        }
        return this;
    }

    private static CharSequence validateMdcEntryKey(final CharSequence key) {
        return argumentNotEmpty(key, "key");
    }

    private void tryToPutToLocalMdc(final String key, final String value) {
        try {
            localMdc.put(key, value);
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    @Override
    public DefaultAutoCloseableSlf4jLogger removeMdcEntry(final CharSequence key) {
        final String keyAsString = validateMdcEntryKey(key).toString();
        tryToRemoveFromLocalMdc(keyAsString);
        MDC.remove(keyAsString);
        return this;
    }

    @Nullable
    private String tryToRemoveFromLocalMdc(final String key) {
        try {
            return localMdc.remove(key);
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
            return null;
        }
    }

    @Override
    public void close() {
        tryToRemoveLocalMdcFromActualMdc();
        tryToClearLocalMdc();
    }

    private void tryToRemoveLocalMdcFromActualMdc() {
        try {
            removeLocalMdcFromActualMdc();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private void removeLocalMdcFromActualMdc() {
        localMdc.forEach((key, value) -> MDC.remove(key));
    }

    private void tryToClearLocalMdc() {
        try {
            localMdc.clear();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

}
