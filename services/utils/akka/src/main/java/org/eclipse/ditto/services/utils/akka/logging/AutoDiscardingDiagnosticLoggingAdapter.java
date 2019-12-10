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

import javax.annotation.concurrent.NotThreadSafe;

import scala.collection.Seq;

/**
 * This implementation of {@link AbstractDiagnosticLoggingAdapter} discards the correlation ID automatically after
 * each log operation.
 */
@NotThreadSafe
final class AutoDiscardingDiagnosticLoggingAdapter extends AbstractDiagnosticLoggingAdapter {

    private final AbstractDiagnosticLoggingAdapter loggingAdapter;

    private AutoDiscardingDiagnosticLoggingAdapter(final AbstractDiagnosticLoggingAdapter loggingAdapter) {
        this.loggingAdapter = checkNotNull(loggingAdapter, "loggingAdapter");
    }

    public static AutoDiscardingDiagnosticLoggingAdapter of(final AbstractDiagnosticLoggingAdapter loggingAdapter) {
        return new AutoDiscardingDiagnosticLoggingAdapter(loggingAdapter);
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
        try {
            loggingAdapter.notifyError(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        try {
            loggingAdapter.notifyError(cause, message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void notifyWarning(final String message) {
        try {
            loggingAdapter.notifyWarning(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void notifyInfo(final String message) {
        try {
            loggingAdapter.notifyInfo(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void notifyDebug(final String message) {
        try {
            loggingAdapter.notifyDebug(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final Throwable cause, final String message) {
        try {
            loggingAdapter.error(cause, message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final Throwable cause, final String template, final Object arg1) {
        try {
            loggingAdapter.error(cause, template, arg1);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2) {

        try {
            loggingAdapter.error(cause, template, arg1, arg2);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        try {
            loggingAdapter.error(cause, template, arg1, arg2, arg3);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final Throwable cause,
            final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        try {
            loggingAdapter.error(cause, template, arg1, arg2, arg3, arg4);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final String message) {
        try {
            loggingAdapter.error(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final String template, final Object arg1) {
        try {
            loggingAdapter.error(template, arg1);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final String template, final Object arg1, final Object arg2) {
        try {
            loggingAdapter.error(template, arg1, arg2);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        try {
            loggingAdapter.error(template, arg1, arg2, arg3);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void error(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        try {
            loggingAdapter.error(template, arg1, arg2, arg3, arg4);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void warning(final String message) {
        try {
            loggingAdapter.warning(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void warning(final String template, final Object arg1) {
        try {
            loggingAdapter.warning(template, arg1);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void warning(final String template, final Object arg1, final Object arg2) {
        try {
            loggingAdapter.warning(template, arg1, arg2);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        try {
            loggingAdapter.warning(template, arg1, arg2, arg3);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void warning(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        try {
            loggingAdapter.warning(template, arg1, arg2, arg3, arg4);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void info(final String message) {
        try {
            loggingAdapter.info(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void info(final String template, final Object arg1) {
        try {
            loggingAdapter.info(template, arg1);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void info(final String template, final Object arg1, final Object arg2) {
        try {
            loggingAdapter.info(template, arg1, arg2);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        try {
            loggingAdapter.info(template, arg1, arg2, arg3);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void info(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        try {
            loggingAdapter.info(template, arg1, arg2, arg3, arg4);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void debug(final String message) {
        try {
            loggingAdapter.debug(message);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void debug(final String template, final Object arg1) {
        try {
            loggingAdapter.debug(template, arg1);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void debug(final String template, final Object arg1, final Object arg2) {
        try {
            loggingAdapter.debug(template, arg1, arg2);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3) {

        try {
            loggingAdapter.debug(template, arg1, arg2, arg3);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public void debug(final String template,
            final Object arg1,
            final Object arg2,
            final Object arg3,
            final Object arg4) {

        try {
            loggingAdapter.debug(template, arg1, arg2, arg3, arg4);
        } finally {
            discardCorrelationId();
        }
    }

    @Override
    public String format(final String t, final Seq<Object> arg) {
        return loggingAdapter.format(t, arg);
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

    @Override
    public void discardCorrelationId() {
        loggingAdapter.discardCorrelationId();
    }

}
