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

import akka.event.DiagnosticLoggingAdapter;

/**
 * Wraps and delegates to a {@link DiagnosticLoggingAdapter}.
 * Furthermore it provides the means to discard the correlation ID.
 */
@NotThreadSafe
final class DefaultDiagnosticLoggingAdapter extends AbstractDiagnosticLoggingAdapter {

    private final DiagnosticLoggingAdapter loggingAdapter;
    private final String loggerName;
    private final Map<String, Object> localMdc;

    private DefaultDiagnosticLoggingAdapter(final DiagnosticLoggingAdapter loggingAdapter,
            final CharSequence loggerName) {

        this.loggingAdapter = checkNotNull(loggingAdapter, "loggingAdapter");
        this.loggerName = argumentNotEmpty(loggerName, "loggerName").toString();
        localMdc = new HashMap<>(5);
    }

    /**
     * Returns an instance of this class.
     *
     * @param loggingAdapter the actual DiagnosticLoggingAdapter to delegate to.
     * @param loggerName the name of the returned logger.
     * @return the instance.
     * @throws NullPointerException if any argument is @code null}.
     * @throws IllegalArgumentException if {@code loggerName} is empty.
     */
    public static DefaultDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter loggingAdapter,
            final CharSequence loggerName) {

        return new DefaultDiagnosticLoggingAdapter(loggingAdapter, loggerName);
    }

    @Override
    public boolean isErrorEnabled() {
        return loggingAdapter.isErrorEnabled();
    }

    @Override
    public boolean isWarningEnabled() {
        return loggingAdapter.isWarningEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return loggingAdapter.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return loggingAdapter.isDebugEnabled();
    }

    @Override
    public void notifyError(final String message) {
        tryToPutLocalMdcToActualMdc();
        loggingAdapter.notifyError(message);
    }

    private void tryToPutLocalMdcToActualMdc() {
        try {
            putLocalMdcToActualMdc();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private void putLocalMdcToActualMdc() {
        if (!localMdc.isEmpty()) {

            // Optimization: only alter actual MDC if local MDC contains entries at all.
            final Map<String, Object> actualMdc = getMDC();
            actualMdc.putAll(localMdc);
            setMDC(actualMdc);
        }
    }

    private void handleConcurrentModificationException() {

        // Logging should not interfere with application's actual work.
        loggingAdapter.warning("This logger <{}> is used by multiple threads!" +
                " Please consider to use a thread-safe logger instead.", getName());
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        tryToPutLocalMdcToActualMdc();
        loggingAdapter.notifyError(cause, message);
    }

    @Override
    public void notifyWarning(final String message) {
        tryToPutLocalMdcToActualMdc();
        loggingAdapter.notifyWarning(message);
    }

    @Override
    public void notifyInfo(final String message) {
        tryToPutLocalMdcToActualMdc();
        loggingAdapter.notifyInfo(message);
    }

    @Override
    public void notifyDebug(final String message) {
        tryToPutLocalMdcToActualMdc();
        loggingAdapter.notifyDebug(message);
    }

    @Override
    public scala.collection.immutable.Map<String, Object> mdc() {
        return loggingAdapter.mdc();
    }

    @Override
    public void mdc(final scala.collection.immutable.Map<String, Object> mdc) {
        loggingAdapter.mdc(mdc);
    }

    @Override
    public Map<String, Object> getMDC() {
        return new HashMap<>(loggingAdapter.getMDC());
    }

    @Override
    public void setMDC(final Map<String, Object> jMdc) {
        loggingAdapter.setMDC(jMdc);
    }

    @Override
    public void clearMDC() {
        loggingAdapter.clearMDC();
    }

    @Override
    public DefaultDiagnosticLoggingAdapter putMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        if (null != value) {
            tryToPutToLocalMdc(validateMdcEntryKey(key).toString(), value);
        } else {
            removeMdcEntry(key);
        }
        return this;
    }

    private void tryToPutToLocalMdc(final String key, final Object value) {
        try {
            localMdc.put(key, value);
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private static CharSequence validateMdcEntryKey(final CharSequence key) {
        return argumentNotEmpty(key, "key");
    }

    @Override
    public DefaultDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        final String keyAsString = validateMdcEntryKey(key).toString();
        if (null != tryToRemoveFromLocalMdc(keyAsString)) {

            // Optimization: only modify actual MDC if local MDC was altered at all.
            final Map<String, Object> actualMdc = getMDC();
            actualMdc.remove(keyAsString);
            setMDC(actualMdc);
        }
        return this;
    }

    @Nullable
    private Object tryToRemoveFromLocalMdc(final String key) {
        try {
            return localMdc.remove(key);
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
            return null;
        }
    }

    @Override
    public DefaultDiagnosticLoggingAdapter discardMdcEntries() {
        tryToRemoveLocalMdcFromActualMdc();
        tryToClearLocalMdc();
        return this;
    }

    private void tryToClearLocalMdc() {
        try {
            localMdc.clear();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private void tryToRemoveLocalMdcFromActualMdc() {
        try {
            removeLocalMdcFromActualMdc();
        } catch (final ConcurrentModificationException e) {
            handleConcurrentModificationException();
        }
    }

    private void removeLocalMdcFromActualMdc() {
        if (!localMdc.isEmpty()) {

            // Optimization: only remove entries from actual MDC if local MDC contains entries at all.
            final Map<String, Object> actualMdc = getMDC();
            localMdc.forEach(actualMdc::remove);
            setMDC(actualMdc);
        }
    }

    @Override
    public String getName() {
        return loggerName;
    }

}
