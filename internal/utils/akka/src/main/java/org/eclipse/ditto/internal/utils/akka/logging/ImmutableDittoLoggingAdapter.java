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
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

import akka.event.DiagnosticLoggingAdapter;
import scala.collection.immutable.Seq;

/**
 * An immutable implementation of {@link ThreadSafeDittoLoggingAdapter}.
 *
 * @since 1.4.0
 */
@Immutable
final class ImmutableDittoLoggingAdapter extends ThreadSafeDittoLoggingAdapter {

    private final Supplier<DiagnosticLoggingAdapter> diagnosticLoggingAdapterFactory;
    private final DiagnosticLoggingAdapter loggingAdapter;

    private ImmutableDittoLoggingAdapter(final Supplier<DiagnosticLoggingAdapter> diagnosticLoggingAdapterFactory) {
        this.diagnosticLoggingAdapterFactory = diagnosticLoggingAdapterFactory;
        loggingAdapter = diagnosticLoggingAdapterFactory.get();
    }

    /**
     * Returns an instance of ImmutableDittoLoggingAdapter.
     *
     * @param diagnosticLoggingAdapterFactory is supposed to provide <em>new</em> DiagnosticLoggingAdapters.
     * @return the instance.
     * @throws NullPointerException if {@code diagnosticLoggingAdapterFactory} is {@code null}.
     */
    static ImmutableDittoLoggingAdapter of(final Supplier<DiagnosticLoggingAdapter> diagnosticLoggingAdapterFactory) {
        return newInstance(checkNotNull(diagnosticLoggingAdapterFactory, "diagnosticLoggingAdapterFactory"), Map.of());
    }

    private static ImmutableDittoLoggingAdapter newInstance(
            final Supplier<DiagnosticLoggingAdapter> diagnosticLoggingAdapterFactory, final Map<String, Object> mdc) {

        final ImmutableDittoLoggingAdapter result = new ImmutableDittoLoggingAdapter(diagnosticLoggingAdapterFactory);
        if (!mdc.isEmpty()) {

            // The initial DiagnosticLoggingAdapter provided by diagnosticLoggingAdapterFactory might already contain
            // MDC entries. Thus the existing MDC should be extended.
            final Map<String, Object> existingMdc = result.getCopyOfMdc();
            existingMdc.putAll(mdc);
            result.setMdc(mdc);
        }
        return result;
    }

    @Override
    public ImmutableDittoLoggingAdapter withCorrelationId(@Nullable final CharSequence correlationId) {
        return withMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
    }

    @Override
    public ImmutableDittoLoggingAdapter withCorrelationId(@Nullable final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(null != withDittoHeaders ? withDittoHeaders.getDittoHeaders() : null);
    }

    @Override
    public ImmutableDittoLoggingAdapter withCorrelationId(@Nullable final DittoHeaders dittoHeaders) {
        return withCorrelationId(null != dittoHeaders ? dittoHeaders.getCorrelationId().orElse(null) : null);
    }

    @Override
    public ImmutableDittoLoggingAdapter discardCorrelationId() {
        return removeMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public ImmutableDittoLoggingAdapter withMdcEntry(final CharSequence key, @Nullable final CharSequence value) {
        validateMdcEntryKey(key, "key");
        final Map<String, Object> currentMdc = getMdc();
        @Nullable final Object existingValue = currentMdc.get(key.toString());
        final ImmutableDittoLoggingAdapter result;
        if (null != value) {
            if (value.equals(existingValue)) {
                result = this;
            } else {
                final Map<String, Object> copyOfMdc = getCopyOfMdc();
                copyOfMdc.put(key.toString(), value.toString());
                result = newInstance(diagnosticLoggingAdapterFactory, copyOfMdc);
            }
        } else {
            if (null == existingValue) {
                result = this;
            } else {
                final Map<String, Object> copyOfMdc = getCopyOfMdc();
                copyOfMdc.remove(key.toString());
                result = newInstance(diagnosticLoggingAdapterFactory, copyOfMdc);
            }
        }
        return result;
    }

    private Map<String, Object> getMdc() {
        return loggingAdapter.getMDC();
    }

    private static void validateMdcEntryKey(final CharSequence key, final String argumentName) {
        argumentNotEmpty(key, argumentName);
    }

    private Map<String, Object> getCopyOfMdc() {
        return new HashMap<>(loggingAdapter.getMDC());
    }

    private void setMdc(final Map<String, Object> mdc) {
        loggingAdapter.setMDC(mdc);
    }

    @Override
    public ImmutableDittoLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");

        final Map<String, Object> mdcCopy = getCopyOfMdc();
        putOrRemove(k1, v1, mdcCopy);
        putOrRemove(k2, v2, mdcCopy);

        return newInstance(diagnosticLoggingAdapterFactory, mdcCopy);
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
    public ImmutableDittoLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        validateMdcEntryKey(k1, "k1");
        validateMdcEntryKey(k2, "k2");
        validateMdcEntryKey(k3, "k3");

        final Map<String, Object> mdcCopy = getCopyOfMdc();
        putOrRemove(k1, v1, mdcCopy);
        putOrRemove(k2, v2, mdcCopy);
        putOrRemove(k3, v3, mdcCopy);

        return newInstance(diagnosticLoggingAdapterFactory, mdcCopy);
    }

    @Override
    public ImmutableDittoLoggingAdapter withMdcEntry(final MdcEntry mdcEntry, final Seq<MdcEntry> furtherMdcEntries) {
        checkNotNull(mdcEntry, "mdcEntry");
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        final Map<String, Object> mdcCopy = getCopyOfMdc();
        mdcCopy.put(mdcEntry.getKey(), mdcEntry.getValueOrNull());

        furtherMdcEntries.foreach(
                furtherMdcEntry -> mdcCopy.put(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull()));

        return newInstance(diagnosticLoggingAdapterFactory, mdcCopy);
    }

    @Override
    public ImmutableDittoLoggingAdapter withMdcEntry(final MdcEntry mdcEntry, final MdcEntry... furtherMdcEntries) {
        checkNotNull(mdcEntry, "mdcEntry");
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        final Map<String, Object> mdcCopy = getCopyOfMdc();
        mdcCopy.put(mdcEntry.getKey(), mdcEntry.getValueOrNull());

        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            mdcCopy.put(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }

        return newInstance(diagnosticLoggingAdapterFactory, mdcCopy);
    }

    @Override
    public ImmutableDittoLoggingAdapter removeMdcEntry(final CharSequence key) {
        validateMdcEntryKey(key, "key");
        final Map<String, Object> currentMdc = getMdc();
        final String keyAsString = key.toString();
        final ImmutableDittoLoggingAdapter result;
        if (currentMdc.containsKey(keyAsString)) {
            final Map<String, Object> copyOfMdc = getCopyOfMdc();
            copyOfMdc.remove(keyAsString);
            result = newInstance(diagnosticLoggingAdapterFactory, copyOfMdc);
        } else {
            result = this;
        }
        return result;
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
        loggingAdapter.notifyError(message);
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        loggingAdapter.notifyError(cause, message);
    }

    @Override
    public void notifyWarning(final String message) {
        loggingAdapter.notifyWarning(message);
    }

    @Override
    public void notifyInfo(final String message) {
        loggingAdapter.notifyInfo(message);
    }

    @Override
    public void notifyDebug(final String message) {
        loggingAdapter.notifyDebug(message);
    }

}
