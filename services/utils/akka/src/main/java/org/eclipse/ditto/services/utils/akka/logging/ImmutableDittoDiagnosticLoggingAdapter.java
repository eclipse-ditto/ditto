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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.event.DiagnosticLoggingAdapter;
import scala.collection.Seq;

/**
 * An immutable implementation of {@link ThreadSafeDittoDiagnosticLoggingAdapter}.
 *
 * @since 1.3.0
 */
@Immutable
final class ImmutableDittoDiagnosticLoggingAdapter extends ThreadSafeDittoDiagnosticLoggingAdapter {

    private final DiagnosticLoggingAdapter plainDiagnosticLoggingAdapter;
    private final Map<String, Object> localMdc;
    private final DiagnosticLoggingAdapter loggingAdapterToUse;

    private ImmutableDittoDiagnosticLoggingAdapter(final DiagnosticLoggingAdapter plainDiagnosticLoggingAdapter,
            final Map<String, Object> localMdc) {

        this.plainDiagnosticLoggingAdapter = plainDiagnosticLoggingAdapter;
        this.localMdc = localMdc;
        if (localMdc.isEmpty()) {
            loggingAdapterToUse = this.plainDiagnosticLoggingAdapter;
        } else {
            loggingAdapterToUse = new MdcUsingLoggingAdapter();
        }
    }

    static ImmutableDittoDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter diagnosticLoggingAdapter) {
        return new ImmutableDittoDiagnosticLoggingAdapter(
                checkNotNull(diagnosticLoggingAdapter, "diagnosticLoggingAdapter"), new HashMap<>(5));
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final CharSequence correlationId) {
        return withMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withCorrelationId(final WithDittoHeaders<?> withDittoHeaders) {
        return withCorrelationId(checkNotNull(withDittoHeaders, "withDittoHeaders").getDittoHeaders());
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withCorrelationId(final DittoHeaders dittoHeaders) {
        return withCorrelationId(checkNotNull(dittoHeaders, "dittoHeaders").getCorrelationId().orElse(null));
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter discardCorrelationId() {
        return removeMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        validateMdcEntryKey(key, "key");
        @Nullable final Object existingValue = localMdc.get(key.toString());
        final ImmutableDittoDiagnosticLoggingAdapter result;
        if (null != value) {
            if (value.equals(existingValue)) {
                result = this;
            } else {
                final Map<String, Object> newLocalMdc = copyLocalMdc();
                newLocalMdc.put(key.toString(), value.toString());
                result = new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
            }
        } else {
            if (null == existingValue) {
                result = this;
            } else {
                final Map<String, Object> newLocalMdc = copyLocalMdc();
                newLocalMdc.remove(key.toString());
                result = new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
            }
        }
        return result;
    }

    private static void validateMdcEntryKey(final CharSequence key, final String argumentName) {
        argumentNotEmpty(key, argumentName);
    }

    private Map<String, Object> copyLocalMdc() {
        return new HashMap<>(localMdc);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");

        final Map<String, Object> newLocalMdc = copyLocalMdc();
        putOrRemove(k1, v1, newLocalMdc);
        putOrRemove(k2, v2, newLocalMdc);

        return new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
    }

    private static void putOrRemove(final CharSequence key, @Nullable final CharSequence value,
            final Map<String, Object> map) {

        if (null == value) {
            map.remove(key.toString());
        } else {
            map.put(key.toString(), value.toString());
        }
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");
        validateMdcEntryKey(k3, "k3");

        final Map<String, Object> newLocalMdc = copyLocalMdc();
        putOrRemove(k1, v1, newLocalMdc);
        putOrRemove(k2, v2, newLocalMdc);
        putOrRemove(k3, v3, newLocalMdc);

        return new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry,
            final Seq<MdcEntry> furtherMdcEntries) {

        checkNotNull(mdcEntry, "mdcEntry");
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        final Map<String, Object> newLocalMdc = copyLocalMdc();
        newLocalMdc.put(mdcEntry.getKey(), mdcEntry.getValueOrNull());

        furtherMdcEntries.foreach(
                furtherMdcEntry -> newLocalMdc.put(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull()));

        return new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry,
            final MdcEntry... furtherMdcEntries) {

        checkNotNull(mdcEntry, "mdcEntry");
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        final Map<String, Object> newLocalMdc = copyLocalMdc();
        newLocalMdc.put(mdcEntry.getKey(), mdcEntry.getValueOrNull());

        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            newLocalMdc.put(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }

        return new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
    }

    @Override
    public ImmutableDittoDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        validateMdcEntryKey(key, "key");
        final ImmutableDittoDiagnosticLoggingAdapter result;
        if (localMdc.containsKey(key.toString())) {
            final Map<String, Object> newLocalMdc = copyLocalMdc();
            newLocalMdc.remove(key.toString());
            result = new ImmutableDittoDiagnosticLoggingAdapter(plainDiagnosticLoggingAdapter, newLocalMdc);
        } else {
            result = this;
        }
        return result;
    }

    @Override
    public boolean isErrorEnabled() {
        return loggingAdapterToUse.isErrorEnabled();
    }

    @Override
    public boolean isWarningEnabled() {
        return loggingAdapterToUse.isWarningEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return loggingAdapterToUse.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return loggingAdapterToUse.isDebugEnabled();
    }

    @Override
    public void notifyError(final String message) {
        loggingAdapterToUse.notifyError(message);
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        loggingAdapterToUse.notifyError(cause, message);
    }

    @Override
    public void notifyWarning(final String message) {
        loggingAdapterToUse.notifyWarning(message);
    }

    @Override
    public void notifyInfo(final String message) {
        loggingAdapterToUse.notifyInfo(message);
    }

    @Override
    public void notifyDebug(final String message) {
        loggingAdapterToUse.notifyDebug(message);
    }

    /**
     * This class extends {@link AbstractDiagnosticLoggingAdapter} as implementing the DiagnosticLoggingAdapter trait
     * would not compile.
     * Not all methods of AbstractDiagnosticLoggingAdapter are actually required.
     * Those throw an UnsupportedOperationException.
     */
    @Immutable
    private final class MdcUsingLoggingAdapter extends AbstractDiagnosticLoggingAdapter {

        private MdcUsingLoggingAdapter() {
            super();
        }

        @Override
        public boolean isErrorEnabled() {
            return plainDiagnosticLoggingAdapter.isErrorEnabled();
        }

        @Override
        public boolean isWarningEnabled() {
            return plainDiagnosticLoggingAdapter.isWarningEnabled();
        }

        @Override
        public boolean isInfoEnabled() {
            return plainDiagnosticLoggingAdapter.isInfoEnabled();
        }

        @Override
        public boolean isDebugEnabled() {
            return plainDiagnosticLoggingAdapter.isDebugEnabled();
        }

        @Override
        public void notifyError(final String message) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainDiagnosticLoggingAdapter.notifyError(message);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        private void putLocalMdcToActualMdc() {
            if (!localMdc.isEmpty()) {
                final Map<String, Object> actualMdc = getMDC();
                actualMdc.putAll(localMdc);
                setMDC(actualMdc);
            }
        }

        private void removeLocalMdcFromActualMdc() {
            if (!localMdc.isEmpty()) {
                final Map<String, Object> actualMdc = getMDC();
                localMdc.forEach(actualMdc::remove);
                setMDC(actualMdc);
            }
        }

        @Override
        public void notifyError(final Throwable cause, final String message) {
            if (isErrorEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainDiagnosticLoggingAdapter.notifyError(cause, message);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void notifyWarning(final String message) {
            if (isWarningEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainDiagnosticLoggingAdapter.notifyWarning(message);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void notifyInfo(final String message) {
            if (isInfoEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainDiagnosticLoggingAdapter.notifyInfo(message);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public void notifyDebug(final String message) {
            if (isDebugEnabled()) {
                try {
                    putLocalMdcToActualMdc();
                    plainDiagnosticLoggingAdapter.notifyDebug(message);
                } finally {
                    removeLocalMdcFromActualMdc();
                }
            }
        }

        @Override
        public Map<String, Object> getMDC() {
            return plainDiagnosticLoggingAdapter.getMDC();
        }

        @Override
        public void setMDC(final Map<String, Object> jMdc) {
            plainDiagnosticLoggingAdapter.setMDC(jMdc);
        }

        @Override
        public MdcUsingLoggingAdapter setCorrelationId(@Nullable final CharSequence correlationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discardCorrelationId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MdcUsingLoggingAdapter putMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MdcUsingLoggingAdapter removeMdcEntry(final CharSequence key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MdcUsingLoggingAdapter discardMdcEntries() {
            throw new UnsupportedOperationException();
        }

    }

}
