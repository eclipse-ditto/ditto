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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.event.DiagnosticLoggingAdapter;
import scala.collection.Seq;
import scala.collection.immutable.Map;

/**
 * Wraps and delegates to a {@link DiagnosticLoggingAdapter}.
 * Furthermore it provides the means to discard the correlation ID.
 */
@NotThreadSafe
final class DefaultDiagnosticLoggingAdapter extends AbstractDiagnosticLoggingAdapter {

    private final DiagnosticLoggingAdapter loggingAdapter;
    @Nullable private CharSequence correlationId;

    private DefaultDiagnosticLoggingAdapter(final DiagnosticLoggingAdapter loggingAdapter) {
        this.loggingAdapter = checkNotNull(loggingAdapter, "loggingAdapter");
        correlationId = null;
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
    public void error(final Throwable cause, final String message) {
        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(cause, message);
        }
    }

    private void putCorrelationIdToMdc() {
        if (null != correlationId) {
            LogUtil.enhanceLogWithCustomField(loggingAdapter, LogUtil.X_CORRELATION_ID, correlationId.toString());
        }
    }

    @Override
    public void error(final Throwable cause, final String template, final Object arg1) {
        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(cause, template, arg1);
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2) {

        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(cause, template, arg1, arg2);
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(cause, template, arg1, arg2, arg3);
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(cause, template, arg1, arg2, arg3, arg4);
        }
    }

    @Override
    public void error(final String message) {
        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(message);
        }
    }

    @Override
    public void error(final String template, final Object arg1) {
        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(template, arg1);
        }
    }

    @Override
    public void error(final String template, final Object arg1, final Object arg2) {
        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(template, arg1, arg2);
        }
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(template, arg1, arg2, arg3);
        }
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        if (isErrorEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.error(template, arg1, arg2, arg3, arg4);
        }
    }

    @Override
    public void warning(final String message) {
        if (isWarningEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.warning(message);
        }
    }

    @Override
    public void warning(final String template, final Object arg1) {
        if (isWarningEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.warning(template, arg1);
        }
    }

    @Override
    public void warning(final String template, final Object arg1, final Object arg2) {
        if (isWarningEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.warning(template, arg1, arg2);
        }
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        if (isWarningEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.warning(template, arg1, arg2, arg3);
        }
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        if (isWarningEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.warning(template, arg1, arg2, arg3, arg4);
        }
    }

    @Override
    public void info(final String message) {
        if (isInfoEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.info(message);
        }
    }

    @Override
    public void info(final String template, final Object arg1) {
        if (isInfoEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.info(template, arg1);
        }
    }

    @Override
    public void info(final String template, final Object arg1, final Object arg2) {
        if (isInfoEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.info(template, arg1, arg2);
        }
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        if (isInfoEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.info(template, arg1, arg2, arg3);
        }
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        if (isInfoEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.info(template, arg1, arg2, arg3, arg4);
        }
    }

    @Override
    public void debug(final String message) {
        if (isDebugEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.debug(message);
        }
    }

    @Override
    public void debug(final String template, final Object arg1) {
        if (isDebugEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.debug(template, arg1);
        }
    }

    @Override
    public void debug(final String template, final Object arg1, final Object arg2) {
        if (isDebugEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.debug(template, arg1, arg2);
        }
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        if (isDebugEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.debug(template, arg1, arg2, arg3);
        }
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        if (isDebugEnabled()) {
            putCorrelationIdToMdc();
            loggingAdapter.debug(template, arg1, arg2, arg3, arg4);
        }
    }

    @Override
    public String format(final String t, final Seq<Object> arg) {
        return loggingAdapter.format(t, arg);
    }

    @Override
    public Map<String, Object> mdc() {
        return loggingAdapter.mdc();
    }

    @Override
    public void mdc(final Map<String, Object> mdc) {
        loggingAdapter.mdc(mdc);
    }

    @Override
    public java.util.Map<String, Object> getMDC() {
        return loggingAdapter.getMDC();
    }

    @Override
    public void setMDC(final java.util.Map<String, Object> jMdc) {
        loggingAdapter.setMDC(jMdc);
    }

    @Override
    public void clearMDC() {
        loggingAdapter.clearMDC();
    }

    @Override
    public DefaultDiagnosticLoggingAdapter setCorrelationId(@Nullable final CharSequence correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    @Override
    public void discardCorrelationId() {
        correlationId = null;
        LogUtil.removeCustomField(loggingAdapter, LogUtil.X_CORRELATION_ID);
    }

}
