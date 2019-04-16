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

package org.eclipse.ditto.services.connectivity.messaging.monitoring;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ImmutableInfoProvider;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.ConnectionMetricsCollector;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.base.Signal;

// TODO: doc
public interface ConnectionMonitor {

    ConnectionLogger getLogger();

    ConnectionMetricsCollector getCounter();

    default void success(final Signal<?> signal) {
        success(ImmutableInfoProvider.forSignal(signal));
    }

    default void success(final ExternalMessage externalMessage) {
        success(ImmutableInfoProvider.forExternalMessage(externalMessage));
    }

    default void success(final InfoProvider infoProvider) {
        getLogger().success(infoProvider);
        getCounter().recordSuccess();
    }

    default void success(final Signal<?> signal, final String message, final Object... messageArguments) {
        getLogger().success(ImmutableInfoProvider.forSignal(signal), message, messageArguments);
        getCounter().recordSuccess();
    }

    default void failure(final Signal<?> signal) {
        getLogger().failure(ImmutableInfoProvider.forSignal(signal));
        getCounter().recordFailure();
    }

    default void failure(final Signal<?> signal, final DittoRuntimeException dittoRuntimeException) {
        failure(ImmutableInfoProvider.forSignal(signal), dittoRuntimeException);
    }

    default void failure(final InfoProvider infoProvider, final DittoRuntimeException dittoRuntimeException) {
        getLogger().failure(infoProvider, dittoRuntimeException);
        getCounter().recordFailure();
    }

    default void failure(final DittoRuntimeException dittoRuntimeException) {
        failure(ImmutableInfoProvider.empty(), dittoRuntimeException);
    }

    default void failure(final Signal<?> signal, final String message, final Object... messageArguments) {
        getLogger().failure(ImmutableInfoProvider.forSignal(signal), message, messageArguments);
        getCounter().recordFailure();
    }

    default void failure(final Map<String, String> headers, final DittoRuntimeException dittoRuntimeException) {
        failure(ImmutableInfoProvider.forHeaders(headers), dittoRuntimeException);
    }

    default void failure(final ExternalMessage externalMessage, final DittoRuntimeException dittoRuntimeException) {
        failure(ImmutableInfoProvider.forExternalMessage(externalMessage), dittoRuntimeException);
    }

    default void failure(final ExternalMessage externalMessage, final String message, final Object... messageArguments) {
        getLogger().failure(ImmutableInfoProvider.forExternalMessage(externalMessage), message, messageArguments);
        getCounter().recordFailure();
    }

    default void exception(final Signal<?> signal, final Exception exception) {
        exception(ImmutableInfoProvider.forSignal(signal), exception);
    }

    default void exception(final Exception exception) {
        exception(ImmutableInfoProvider.empty(), exception);
    }

    default void exception(final Map<String, String> headers, final Exception exception) {
        exception(ImmutableInfoProvider.forHeaders(headers), exception);
    }

    default void exception(final ExternalMessage externalMessage, final Exception exception) {
        exception(ImmutableInfoProvider.forExternalMessage(externalMessage), exception);
    }

    default void exception(final InfoProvider infoProvider, final Exception exception) {
        getLogger().exception(infoProvider, exception);
        getCounter().recordFailure();
    }

    default void exception(final ExternalMessage externalMessage, final String message, final Object... messageArguments) {
        getLogger().exception(ImmutableInfoProvider.forExternalMessage(externalMessage), message, messageArguments);
        getCounter().recordFailure();
    }

    default WrappedConnectionMonitor wrapExecution(final ExternalMessage message) {
        return wrapExecution(Collections.singleton(this), message);
    }

    default WrappedConnectionMonitor wrapExecution(final Signal<?> signal) {
        return wrapExecution(Collections.singleton(this), signal);
    }

    static WrappedConnectionMonitor wrapExecution(final Collection<ConnectionMonitor> monitors,
            final Signal signal) {
        return wrapExecution(monitors, ImmutableInfoProvider.forSignal(signal));
    }

    static WrappedConnectionMonitor wrapExecution(final Collection<ConnectionMonitor> monitors,
            final ExternalMessage message) {
        return wrapExecution(monitors, ImmutableInfoProvider.forExternalMessage(message));
    }

    static WrappedConnectionMonitor wrapExecution(final Collection<ConnectionMonitor> monitors,
            final InfoProvider infoProvider) {
        return new WrappedConnectionMonitor() {
            @Override
            public <T> T execute(final Supplier<T> supplier) {
                return wrapExecution(supplier,
                        () -> monitors.forEach(monitor -> monitor.success(infoProvider)),
                        dittoRuntimeException -> monitors.forEach(
                                monitor -> monitor.failure(infoProvider, dittoRuntimeException)),
                        exception -> monitors.forEach(monitor -> monitor.exception(infoProvider, exception)));
            }

            @Override
            public void execute(final Runnable runnable) {
                wrapExecution(runnable,
                        () -> monitors.forEach(monitor -> monitor.success(infoProvider)),
                        dittoRuntimeException -> monitors.forEach(
                                monitor -> monitor.failure(infoProvider, dittoRuntimeException)),
                        exception -> monitors.forEach(monitor -> monitor.exception(infoProvider, exception)));

            }
        };
    }

    static void wrapExecution(final Runnable runnable,
            final Runnable onSuccess,
            final Consumer<DittoRuntimeException> onFailure,
            final Consumer<Exception> onException) {
        try {
            runnable.run();
            onSuccess.run();
        } catch (final DittoRuntimeException runtimeException) {
            onFailure.accept(runtimeException);
            throw runtimeException;
        } catch (final Exception exception) {
            onException.accept(exception);
            throw exception;
        }
    }

    static <T> T wrapExecution(final Supplier<T> supplier,
            final Runnable onSuccess,
            final Consumer<DittoRuntimeException> onFailure,
            final Consumer<Exception> onException) {
        try {
            final T result = supplier.get();
            onSuccess.run();
            return result;
        } catch (final DittoRuntimeException runtimeException) {
            onFailure.accept(runtimeException);
            throw runtimeException;
        } catch (final Exception exception) {
            onException.accept(exception);
            throw exception;
        }
    }


    interface WrappedConnectionMonitor {

        <T> T execute(final Supplier<T> supplier);

        void execute(final Runnable runnable);

    }

    interface Builder {

        ConnectionMonitor build();

    }

    interface InfoProvider {

        String getCorrelationId();

        Instant getTimestamp();

        @Nullable
        String getThingId();

    }

}
