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

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

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
            discardMdcEntries();
        }
    }

    @Override
    public void notifyError(final Throwable cause, final String message) {
        try {
            loggingAdapter.notifyError(cause, message);
        } finally {
            discardMdcEntries();
        }
    }

    @Override
    public void notifyWarning(final String message) {
        try {
            loggingAdapter.notifyWarning(message);
        } finally {
            discardMdcEntries();
        }
    }

    @Override
    public void notifyInfo(final String message) {
        try {
            loggingAdapter.notifyInfo(message);
        } finally {
            discardMdcEntries();
        }
    }

    @Override
    public void notifyDebug(final String message) {
        try {
            loggingAdapter.notifyDebug(message);
        } finally {
            discardMdcEntries();
        }
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
    public AutoDiscardingDiagnosticLoggingAdapter putMdcEntry(final CharSequence key,
            @Nullable final CharSequence value) {
        loggingAdapter.putMdcEntry(key, value);
        return this;
    }

    @Override
    public AutoDiscardingDiagnosticLoggingAdapter removeMdcEntry(final CharSequence key) {
        loggingAdapter.removeMdcEntry(key);
        return this;
    }

    @Override
    public AutoDiscardingDiagnosticLoggingAdapter discardMdcEntries() {
        loggingAdapter.discardMdcEntries();
        return this;
    }

    @Override
    public String getName() {
        return loggingAdapter.getName();
    }

}
