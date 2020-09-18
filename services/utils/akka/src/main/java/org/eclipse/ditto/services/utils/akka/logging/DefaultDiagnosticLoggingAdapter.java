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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
    private final Map<String, Object> localMdc;

    private DefaultDiagnosticLoggingAdapter(final DiagnosticLoggingAdapter loggingAdapter) {
        this.loggingAdapter = checkNotNull(loggingAdapter, "loggingAdapter");
        localMdc = new HashMap<>(5);
    }

    /**
     * Returns an instance of this class.
     *
     * @param loggingAdapter the actual DiagnosticLoggingAdapter to delegate to.
     * @return the instance.
     * @throws NullPointerException if {@code loggingAdapter} is {@code null}.
     */
    public static DefaultDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter loggingAdapter) {
        return new DefaultDiagnosticLoggingAdapter(loggingAdapter);
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
        putLocalMdcToActualMdc();
        loggingAdapter.notifyError(message);
    }

    private void putLocalMdcToActualMdc() {
        if (!localMdc.isEmpty()) {

            // Optimization: only alter actual MDC if local MDC contains entries at all.
            final Map<String, Object> actualMdc = getMDC();
            actualMdc.putAll(localMdc);
            setMDC(actualMdc);
        }
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        putLocalMdcToActualMdc();
        loggingAdapter.notifyError(cause, message);
    }

    @Override
    public void notifyWarning(final String message) {
        putLocalMdcToActualMdc();
        loggingAdapter.notifyWarning(message);
    }

    @Override
    public void notifyInfo(final String message) {
        putLocalMdcToActualMdc();
        loggingAdapter.notifyInfo(message);
    }

    @Override
    public void notifyDebug(final String message) {
        putLocalMdcToActualMdc();
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
            localMdc.put(validateMdcEntryKey(key).toString(), value);
        } else {
            removeMdcEntry(key);
        }
        return this;
    }

    private static CharSequence validateMdcEntryKey(final CharSequence key) {
        return argumentNotEmpty(key, "key");
    }

    @Override
    public DefaultDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        final String keyAsString = validateMdcEntryKey(key).toString();
        if (null != localMdc.remove(keyAsString)) {

            // Optimization: only modify actual MDC if local MDC was altered at all.
            final Map<String, Object> actualMdc = getMDC();
            actualMdc.remove(keyAsString);
            setMDC(actualMdc);
        }
        return this;
    }

    @Override
    public DefaultDiagnosticLoggingAdapter discardMdcEntries() {
        removeLocalMdcFromActualMdc();
        localMdc.clear();
        return this;
    }

    private void removeLocalMdcFromActualMdc() {
        if (!localMdc.isEmpty()) {

            // Optimization: only remove entries from actual MDC if local MDC contains entries at all.
            final Map<String, Object> actualMdc = getMDC();
            localMdc.forEach(actualMdc::remove);
            setMDC(actualMdc);
        }
    }

}
