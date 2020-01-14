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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.event.DiagnosticLoggingAdapter;
import scala.collection.Seq;

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
        currentLogger = loggingAdapter;
    }

    /**
     * Returns an instance of the default Ditto DiagnosticLoggingAdapter.
     *
     * @param diagnosticLoggingAdapter the Akka DiagnosticLoggingAdapter which performs the actual logging.
     * @return the instance.
     * @throws NullPointerException if {@code diagnosticLoggingAdapter} is {@code null}.
     */
    public static DefaultDittoDiagnosticLoggingAdapter of(final DiagnosticLoggingAdapter diagnosticLoggingAdapter) {
        final DefaultDiagnosticLoggingAdapter loggingAdapter =
                DefaultDiagnosticLoggingAdapter.of(diagnosticLoggingAdapter);

        return new DefaultDittoDiagnosticLoggingAdapter(loggingAdapter,
                AutoDiscardingDiagnosticLoggingAdapter.of(loggingAdapter));
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(final CharSequence correlationId) {
        currentLogger = autoDiscardingLoggingAdapter;
        setCorrelationId(null != correlationId ? correlationId.toString() : null);
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return withCorrelationId(checkNotNull(withDittoHeaders, "withDittoHeaders").getDittoHeaders());
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter withCorrelationId(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        currentLogger = autoDiscardingLoggingAdapter;
        setCorrelationId(dittoHeaders.getCorrelationId().orElse(null));
        return this;
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(final CharSequence correlationId) {
        currentLogger = loggingAdapter;
        setCorrelationId(null != correlationId ? correlationId.toString() : null);
        return this;
    }

    private void setCorrelationId(@Nullable final String correlationId) {
        LogUtil.enhanceLogWithCustomField(currentLogger, LogUtil.X_CORRELATION_ID, correlationId);
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return setCorrelationId(checkNotNull(withDittoHeaders, "withDittoHeaders").getDittoHeaders());
    }

    @Override
    public DefaultDittoDiagnosticLoggingAdapter setCorrelationId(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        currentLogger = loggingAdapter;
        setCorrelationId(dittoHeaders.getCorrelationId().orElse(null));
        return this;
    }

    @Override
    public void discardCorrelationId() {
        currentLogger.discardCorrelationId();
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
    public void error(final Throwable cause, final String message) {
        currentLogger.error(cause, message);
    }

    @Override
    public void error(final Throwable cause, final String template, final Object arg1) {
        currentLogger.error(cause, template, arg1);
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2) {

        currentLogger.error(cause, template, arg1, arg2);
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        currentLogger.error(cause, template, arg1, arg2, arg3);
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        currentLogger.error(cause, template, arg1, arg2, arg3, arg4);
    }

    @Override
    public void error(final String message) {
        currentLogger.error(message);
    }

    @Override
    public void error(final String template, final Object arg1) {
        currentLogger.error(template, arg1);
    }

    @Override
    public void error(final String template, final Object arg1, final Object arg2) {
        currentLogger.error(template, arg1, arg2);
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        currentLogger.error(template, arg1, arg2, arg3);
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        currentLogger.error(template, arg1, arg2, arg3, arg4);
    }

    @Override
    public void warning(final String message) {
        currentLogger.warning(message);
    }

    @Override
    public void warning(final String template, final Object arg1) {
        currentLogger.warning(template, arg1);
    }

    @Override
    public void warning(final String template, final Object arg1, final Object arg2) {
        currentLogger.warning(template, arg1, arg2);
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        currentLogger.warning(template, arg1, arg2, arg3);
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        currentLogger.warning(template, arg1, arg2, arg3, arg4);
    }

    @Override
    public void info(final String message) {
        currentLogger.info(message);
    }

    @Override
    public void info(final String template, final Object arg1) {
        currentLogger.info(template, arg1);
    }

    @Override
    public void info(final String template, final Object arg1, final Object arg2) {
        currentLogger.info(template, arg1, arg2);
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        currentLogger.info(template, arg1, arg2, arg3);
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        currentLogger.info(template, arg1, arg2, arg3, arg4);
    }

    @Override
    public void debug(final String message) {
        currentLogger.debug(message);
    }

    @Override
    public void debug(final String template, final Object arg1) {
        currentLogger.debug(template, arg1);
    }

    @Override
    public void debug(final String template, final Object arg1, final Object arg2) {
        currentLogger.debug(template, arg1, arg2);
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        currentLogger.debug(template, arg1, arg2, arg3);
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        currentLogger.debug(template, arg1, arg2, arg3, arg4);
    }

    @Override
    public String format(final String t, final Seq<Object> arg) {
        return currentLogger.format(t, arg);
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
