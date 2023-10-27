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
package org.eclipse.ditto.internal.utils.pekko.logging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.apache.pekko.event.DiagnosticLoggingAdapter;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

import scala.collection.immutable.Seq;

/**
 * Default implementation of {@link DittoDiagnosticLoggingAdapter}.
 * It harnesses the state pattern for determining how long a particular correlation ID is valid.
 */
@NotThreadSafe
final class DefaultDittoDiagnosticLoggingAdapter extends DittoDiagnosticLoggingAdapter {

    private final AbstractDiagnosticLoggingAdapter loggingAdapter;

    private DefaultDittoDiagnosticLoggingAdapter(final AbstractDiagnosticLoggingAdapter loggingAdapter) {

        this.loggingAdapter = loggingAdapter;
    }

    /**
     * Returns an instance of the default Ditto DiagnosticLoggingAdapter.
     *
     * @param diagnosticLoggingAdapter the Pekko DiagnosticLoggingAdapter which performs the actual logging.
     * @param loggerName the name of the returned logger.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code loggerName} is empty.
     */
    public static DefaultDittoDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter diagnosticLoggingAdapter,
            final CharSequence loggerName) {

        final DefaultDiagnosticLoggingAdapter loggingAdapter =
                DefaultDiagnosticLoggingAdapter.of(diagnosticLoggingAdapter, loggerName);

        return new DefaultDittoDiagnosticLoggingAdapter(loggingAdapter);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final CharSequence correlationId) {
        loggingAdapter.putMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final Map<String, String> headers) {
        return withMdcEntries(CommonMdcEntryKey.extractMdcEntriesFromHeaders(headers));
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(null != withDittoHeaders ? withDittoHeaders.getDittoHeaders() : null);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final DittoHeaders dittoHeaders) {
        return withCorrelationId(null != dittoHeaders ? dittoHeaders : Map.of());
    }

    @Override
    public void discardCorrelationId() {
        discardMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public void discardMdcEntry(final CharSequence key) {
        removeFromMdcOfAllLoggerStates(key);
    }

    private void removeFromMdcOfAllLoggerStates(final CharSequence key) {
        loggingAdapter.removeMdcEntry(key);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter putMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        loggingAdapter.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        loggingAdapter.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        loggingAdapter.putMdcEntry(k1, v1);
        loggingAdapter.putMdcEntry(k2, v2);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        loggingAdapter.putMdcEntry(k1, v1);
        loggingAdapter.putMdcEntry(k2, v2);
        loggingAdapter.putMdcEntry(k3, v3);
        return this;
    }

    @Override
    public DittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry, final MdcEntry... furtherMdcEntries) {
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        loggingAdapter.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            loggingAdapter.putMdcEntry(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntries(final Collection<MdcEntry> mdcEntries) {
        checkNotNull(mdcEntries, "mdcEntries");

        mdcEntries.forEach(mdcEntry -> loggingAdapter.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull()));
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry,
            final Seq<MdcEntry> furtherMdcEntries) {

        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        loggingAdapter.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        furtherMdcEntries.foreach(furtherMdcEntry -> loggingAdapter.putMdcEntry(furtherMdcEntry.getKey(),
                furtherMdcEntry.getValueOrNull()));
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        loggingAdapter.removeMdcEntry(key);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter discardMdcEntries() {
        loggingAdapter.discardMdcEntries();
        return this;
    }

    @Override
    public String getName() {
        return loggingAdapter.getName();
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
        return loggingAdapter.getMDC();
    }

    @Override
    public void setMDC(final Map<String, Object> jMdc) {
        loggingAdapter.setMDC(jMdc);
    }

    @Override
    public void clearMDC() {
        loggingAdapter.clearMDC();
    }

}
