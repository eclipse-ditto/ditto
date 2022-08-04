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

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

import akka.event.DiagnosticLoggingAdapter;
import scala.collection.JavaConverters;
import scala.collection.immutable.Seq;

/**
 * Default implementation of {@link DittoDiagnosticLoggingAdapter}.
 * It harnesses the state pattern for determining how long a particular correlation ID is valid.
 */
@NotThreadSafe
final class DefaultDittoDiagnosticLoggingAdapter extends DittoDiagnosticLoggingAdapter {

    private final AbstractDiagnosticLoggingAdapter loggingAdapter;
    private final AbstractDiagnosticLoggingAdapter autoDiscardingLoggingAdapter;
    private AbstractDiagnosticLoggingAdapter currentLogger;

    private DefaultDittoDiagnosticLoggingAdapter(final AbstractDiagnosticLoggingAdapter loggingAdapter,
            final AbstractDiagnosticLoggingAdapter autoDiscardingLoggingAdapter) {

        this.loggingAdapter = loggingAdapter;
        this.autoDiscardingLoggingAdapter = autoDiscardingLoggingAdapter;
        currentLogger = autoDiscardingLoggingAdapter;
    }

    /**
     * Returns an instance of the default Ditto DiagnosticLoggingAdapter.
     *
     * @param diagnosticLoggingAdapter the Akka DiagnosticLoggingAdapter which performs the actual logging.
     * @param loggerName the name of the returned logger.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code loggerName} is empty.
     */
    public static DefaultDittoDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter diagnosticLoggingAdapter,
            final CharSequence loggerName) {

        final DefaultDiagnosticLoggingAdapter loggingAdapter =
                DefaultDiagnosticLoggingAdapter.of(diagnosticLoggingAdapter, loggerName);

        return new DefaultDittoDiagnosticLoggingAdapter(loggingAdapter,
                AutoDiscardingDiagnosticLoggingAdapter.of(loggingAdapter));
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final CharSequence correlationId) {
        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(null != withDittoHeaders ? withDittoHeaders.getDittoHeaders() : null);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(@Nullable final DittoHeaders dittoHeaders) {
        return withCorrelationId(null != dittoHeaders ? dittoHeaders.getCorrelationId().orElse(null) : null);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(@Nullable final CharSequence correlationId) {
        return setMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return setCorrelationId(checkNotNull(withDittoHeaders, "withDittoHeaders").getDittoHeaders());
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        return setCorrelationId(dittoHeaders.getCorrelationId().orElse(null));
    }

    @Override
    public void discardCorrelationId() {
        discardMdcEntry(CommonMdcEntryKey.CORRELATION_ID);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        putToMdcOfAllLoggerStates(key, value);
        currentLogger = loggingAdapter;
        return this;
    }

    private void putToMdcOfAllLoggerStates(final CharSequence key, @Nullable final CharSequence value) {
        loggingAdapter.putMdcEntry(key, value);
        autoDiscardingLoggingAdapter.putMdcEntry(key, value);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        putToMdcOfAllLoggerStates(k1, v1);
        putToMdcOfAllLoggerStates(k2, v2);
        currentLogger = loggingAdapter;
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        putToMdcOfAllLoggerStates(k1, v1);
        putToMdcOfAllLoggerStates(k2, v2);
        putToMdcOfAllLoggerStates(k3, v3);
        currentLogger = loggingAdapter;
        return this;
    }

    @Override
    public void discardMdcEntry(final CharSequence key) {
        removeFromMdcOfAllLoggerStates(key);
        currentLogger = loggingAdapter;
    }

    private void removeFromMdcOfAllLoggerStates(final CharSequence key) {
        loggingAdapter.removeMdcEntry(key);
        autoDiscardingLoggingAdapter.removeMdcEntry(key);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setMdcEntry(final MdcEntry mdcEntry,
            final Seq<MdcEntry> furtherMdcEntries) {

        currentLogger = loggingAdapter;
        putToMdcOfAllLoggerStates(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        final Collection<MdcEntry> furtherMdcEntriesCollection = JavaConverters.asJavaCollection(furtherMdcEntries);
        furtherMdcEntriesCollection.forEach(furtherMdcEntry -> putToMdcOfAllLoggerStates(furtherMdcEntry.getKey(),
                furtherMdcEntry.getValueOrNull()));
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setMdcEntry(final MdcEntry mdcEntry,
            final MdcEntry... furtherMdcEntries) {

        currentLogger = loggingAdapter;
        putToMdcOfAllLoggerStates(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            putToMdcOfAllLoggerStates(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter putMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        currentLogger = loggingAdapter;
        currentLogger.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {

        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(key, value);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2) {

        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(k1, v1);
        currentLogger.putMdcEntry(k2, v2);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntries(final CharSequence k1, @Nullable final CharSequence v1,
            final CharSequence k2, @Nullable final CharSequence v2,
            final CharSequence k3, @Nullable final CharSequence v3) {

        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(k1, v1);
        currentLogger.putMdcEntry(k2, v2);
        currentLogger.putMdcEntry(k3, v3);
        return this;
    }

    @Override
    public DittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry, final MdcEntry... furtherMdcEntries) {
        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        for (final MdcEntry furtherMdcEntry : furtherMdcEntries) {
            currentLogger.putMdcEntry(furtherMdcEntry.getKey(), furtherMdcEntry.getValueOrNull());
        }
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withMdcEntry(final MdcEntry mdcEntry,
            final Seq<MdcEntry> furtherMdcEntries) {

        checkNotNull(furtherMdcEntries, "furtherMdcEntries");

        currentLogger = autoDiscardingLoggingAdapter;
        currentLogger.putMdcEntry(mdcEntry.getKey(), mdcEntry.getValueOrNull());
        furtherMdcEntries.foreach(furtherMdcEntry -> currentLogger.putMdcEntry(furtherMdcEntry.getKey(),
                furtherMdcEntry.getValueOrNull()));
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        currentLogger.removeMdcEntry(key);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter discardMdcEntries() {
        currentLogger.discardMdcEntries();
        currentLogger = autoDiscardingLoggingAdapter;
        return this;
    }

    @Override
    public String getName() {
        return currentLogger.getName();
    }

    @Override
    public boolean isErrorEnabled() {
        return currentLogger.isErrorEnabled();
    }

    @Override
    public boolean isWarningEnabled() {
        return currentLogger.isWarningEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return currentLogger.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return currentLogger.isDebugEnabled();
    }

    @Override
    public void notifyError(final String message) {
        currentLogger.notifyError(message);
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        currentLogger.notifyError(cause, message);
    }

    @Override
    public void notifyWarning(final String message) {
        currentLogger.notifyWarning(message);
    }

    @Override
    public void notifyInfo(final String message) {
        currentLogger.notifyInfo(message);
    }

    @Override
    public void notifyDebug(final String message) {
        currentLogger.notifyDebug(message);
    }

    @Override
    public scala.collection.immutable.Map<String, Object> mdc() {
        return currentLogger.mdc();
    }

    @Override
    public void mdc(final scala.collection.immutable.Map<String, Object> mdc) {
        currentLogger.mdc(mdc);
    }

    @Override
    public Map<String, Object> getMDC() {
        return currentLogger.getMDC();
    }

    @Override
    public void setMDC(final Map<String, Object> jMdc) {
        currentLogger.setMDC(jMdc);
    }

    @Override
    public void clearMDC() {
        currentLogger.clearMDC();
    }

}
