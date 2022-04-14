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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter;

/**
 * An abstraction for connection monitoring that currently encapsulates metrics and logging into one interface.
 */
public interface ConnectionMonitor {

    /**
     * Get the logger that is used by the monitor.
     *
     * @return the logger.
     */
    ConnectionLogger getLogger();

    /**
     * Get the counter that is used by the monitor.
     *
     * @return the counter.
     */
    ConnectionMetricsCounter getCounter();

    /**
     * Record a success event.
     *
     * @param signal that was processed during the success event.
     */
    default void success(final Signal<?> signal) {
        success(InfoProviderFactory.forSignal(signal));
    }

    /**
     * Record a success event.
     *
     * @param externalMessage that was processed during the success event.
     */
    default void success(final ExternalMessage externalMessage) {
        success(InfoProviderFactory.forExternalMessage(externalMessage));
    }

    /**
     * Record a success event.
     *
     * @param infoProvider that provides useful information for the success event.
     */
    default void success(final InfoProvider infoProvider) {
        getLogger().success(infoProvider);
        getCounter().recordSuccess();
    }

    /**
     * Record a success event.
     *
     * @param infoProvider that provides useful information for the success event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void success(final InfoProvider infoProvider, final String message, final Object... messageArguments) {
        getLogger().success(infoProvider, message, messageArguments);
        getCounter().recordSuccess();
    }

    /**
     * Record a success event.
     *
     * @param signal that was processed during the success event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void success(final Signal<?> signal, final String message, final Object... messageArguments) {
        getLogger().success(InfoProviderFactory.forSignal(signal), message, messageArguments);
        getCounter().recordSuccess();
    }

    /**
     * Record a success event.
     *
     * @param externalMessage that was processed during the success event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void success(final ExternalMessage externalMessage, final String message,
            final Object... messageArguments) {
        getLogger().success(InfoProviderFactory.forExternalMessage(externalMessage), message, messageArguments);
        getCounter().recordSuccess();
    }

    /**
     * Record a failure event.
     *
     * @param signal that was processed during the failure.
     */
    default void failure(final Signal<?> signal) {
        getLogger().failure(InfoProviderFactory.forSignal(signal));
        getCounter().recordFailure();
    }

    /**
     * Record a failure event.
     *
     * @param signal that was processed during the failure.
     * @param dittoRuntimeException the exception that caused the failure. Its message will be used in the log message.
     */
    default void failure(final Signal<?> signal, @Nullable final DittoRuntimeException dittoRuntimeException) {
        failure(InfoProviderFactory.forSignal(signal), dittoRuntimeException);
    }

    /**
     * Record a failure event.
     *
     * @param infoProvider that provides useful information for the failure.
     * @param dittoRuntimeException the exception that caused the failure. Its message will be used in the log message.
     */
    default void failure(final InfoProvider infoProvider, @Nullable final DittoRuntimeException dittoRuntimeException) {
        getLogger().failure(infoProvider, dittoRuntimeException);
        getCounter().recordFailure();
    }

    /**
     * Record a failure event.
     *
     * @param dittoRuntimeException the exception that caused the failure. Its message will be used in the log message.
     */
    default void failure(final DittoRuntimeException dittoRuntimeException) {
        failure(InfoProviderFactory.empty(), dittoRuntimeException);
    }

    /**
     * Record a failure event.
     *
     * @param signal that was processed during the failure.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void failure(final Signal<?> signal, final String message, final Object... messageArguments) {
        getLogger().failure(InfoProviderFactory.forSignal(signal), message, messageArguments);
        getCounter().recordFailure();
    }

    /**
     * Record a failure event.
     *
     * @param headers that were processed during the failure.
     * @param dittoRuntimeException the exception that caused the failure. Its message will be used in the log message.
     */
    default void failure(final Map<String, String> headers, final DittoRuntimeException dittoRuntimeException) {
        failure(InfoProviderFactory.forHeaders(headers), dittoRuntimeException);
    }

    /**
     * Record a failure event.
     *
     * @param externalMessage that was processed during the failure.
     * @param dittoRuntimeException the exception that caused the failure. Its message will be used in the log message.
     */
    default void failure(final ExternalMessage externalMessage, final DittoRuntimeException dittoRuntimeException) {
        failure(InfoProviderFactory.forExternalMessage(externalMessage), dittoRuntimeException);
    }

    /**
     * Record a failure event.
     *
     * @param externalMessage that was processed during the failure.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void failure(final ExternalMessage externalMessage, final String message,
            final Object... messageArguments) {
        getLogger().failure(InfoProviderFactory.forExternalMessage(externalMessage), message, messageArguments);
        getCounter().recordFailure();
    }

    /**
     * Record an exception event.
     *  @param signal that was processed during the exception.
     * @param exception the exception.
     */
    default void exception(final Signal<?> signal, final Throwable exception) {
        exception(InfoProviderFactory.forSignal(signal), exception);
    }

    /**
     * Record an exception event.
     *
     * @param exception the exception.
     */
    default void exception(final Throwable exception) {
        exception(InfoProviderFactory.empty(), exception);
    }

    /**
     * Record an exception event.
     *  @param headers that were processed during the exception.
     * @param exception the exception.
     */
    default void exception(final Map<String, String> headers, final Throwable exception) {
        exception(InfoProviderFactory.forHeaders(headers), exception);
    }

    /**
     * Record an exception event.
     *  @param externalMessage that was processed during the success event.
     * @param exception the exception that caused the failure.
     */
    default void exception(final ExternalMessage externalMessage, final Throwable exception) {
        exception(InfoProviderFactory.forExternalMessage(externalMessage), exception);
    }

    /**
     * Record an exception event.
     *
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     */
    default void exception(final String message, final Object... messageArguments) {
        getLogger().exception(message, messageArguments);
        getCounter().recordFailure();
    }

    /**
     * Record an exception event.
     *  @param infoProvider that provides useful information for the success event.
     * @param exception the exception that caused the failure.
     */
    default void exception(final InfoProvider infoProvider, final Throwable exception) {
        getLogger().exception(infoProvider, exception);
        getCounter().recordFailure();
    }

    /**
     * Record an exception event.
     *
     * @param headers that were processed during the exception.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void exception(final Map<String, String> headers, final String message, final Object... messageArguments) {
        getLogger().exception(InfoProviderFactory.forHeaders(headers), message, messageArguments);
        getCounter().recordFailure();
    }

    /**
     * Record an exception event.
     *
     * @param externalMessage that was processed during the exception event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    default void exception(final ExternalMessage externalMessage,
            final String message,
            final Object... messageArguments) {

        getLogger().exception(InfoProviderFactory.forExternalMessage(externalMessage), message, messageArguments);
        getCounter().recordFailure();
    }

    /**
     * Starts wrapping the execution of runnable code with additional information provided by {@code message}. It will
     * monitor if the execution succeeded or failed.
     *
     * @param message that provides additional information for monitoring (i.e. the log messages that are generated).
     * @return a wrapped monitor that can execute runnable code.
     */
    default WrappedConnectionMonitor wrapExecution(final ExternalMessage message) {
        return wrapExecution(Collections.singleton(this), message);
    }

    /**
     * Starts wrapping the execution of runnable code with additional information provided by {@code signal}. It will
     * monitor if the execution succeeded or failed.
     *
     * @param signal that provides additional information for monitoring (i.e. the log messages that are generated).
     * @return a wrapped monitor that can execute runnable code.
     */
    default WrappedConnectionMonitor wrapExecution(final Signal<?> signal) {
        return wrapExecution(Collections.singleton(this), signal);
    }

    /**
     * Starts wrapping the execution of runnable code with additional information provided by {@code signal}. It will
     * monitor if the execution succeeded or failed.
     *
     * @param monitors that will monitor if execution succeeded or failed.
     * @param signal that provides additional information for monitoring (i.e. the log messages that are generated).
     * @return a wrapped monitor that can execute runnable code.
     */
    static WrappedConnectionMonitor wrapExecution(final Collection<ConnectionMonitor> monitors,
            final Signal<?> signal) {
        return wrapExecution(monitors, InfoProviderFactory.forSignal(signal));
    }

    /**
     * Starts wrapping the execution of runnable code with additional information provided by {@code message}. It will
     * monitor if the execution succeeded or failed.
     *
     * @param monitors that will monitor if execution succeeded or failed.
     * @param message that provides additional information for monitoring (i.e. the log messages that are generated).
     * @return a wrapped monitor that can execute runnable code.
     */
    static WrappedConnectionMonitor wrapExecution(final Collection<ConnectionMonitor> monitors,
            final ExternalMessage message) {
        return wrapExecution(monitors, InfoProviderFactory.forExternalMessage(message));
    }

    /**
     * Starts wrapping the execution of runnable code with additional information provided by {@code message}. It will
     * monitor if the execution succeeded or failed.
     *
     * @param monitors that will monitor if execution succeeded or failed.
     * @param infoProvider that provides additional information for monitoring (i.e. the log messages that are generated).
     * @return a wrapped monitor that can execute runnable code.
     */
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

    /**
     * Wraps the execution of {@code runnable} and uses the given callbacks to inform about success, failure (if a
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} happened) or exception (any other {@link java.lang.Exception})
     * occured during execution.
     *
     * @param runnable that will be executed.
     * @param onSuccess runnable that will be called if the execution succeeded without failure.
     * @param onFailure consumer that will be called if a failure, caused by a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, happened.
     * @param onException consumer that will be called if an exception, caused by any other {@link java.lang.Exception}, happened.
     */
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

    /**
     * Wraps the execution of {@code supplier} and uses the given callbacks to inform about success, failure (if a
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} happened) or exception (any other {@link java.lang.Exception})
     * occured during execution.
     *
     * @param supplier that will be executed.
     * @param onSuccess runnable that will be called if the execution succeeded without failure.
     * @param onFailure consumer that will be called if a failure, caused by a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, happened.
     * @param onException consumer that will be called if an exception, caused by any other {@link java.lang.Exception}, happened.
     * @param <T> type of value the supplier returns.
     * @return the value returned by {@code supplier}.
     */
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

    /**
     * Defines an connection monitor that wraps an execution and will monitor if execution succeeded or failed.
     */
    interface WrappedConnectionMonitor {

        /**
         * Calls the supplier and returns its value. Also monitors if execution succeeded or failed. In case
         * of a failure, the exception will be rethrown.
         *
         * @param supplier the supplier that will be called.
         * @param <T> type of value that the supplier will return.
         * @return the value returned by {@code supplier}.
         * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if the execution of {@code supplier}
         * throws a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}.
         * throws java.lang.Exception if the execution of {@code supplier} throws an {@link java.lang.Exception}.
         */
        <T> T execute(final Supplier<T> supplier);

        /**
         * Calls the runnable. Also monitors if execution succeeded or failed. In case of a failure, the exception
         * will be rethrown.
         *
         * @param runnable the runnable that will be called.
         * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if the execution of {@code runnable}
         * throws a {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}.
         * throws java.lang.Exception if the execution of {@code runnable} throws an {@link java.lang.Exception}.
         */
        void execute(final Runnable runnable);

    }

    /**
     * Builder for {@code ConnectionMonitor}.
     */
    interface Builder {

        /**
         * Build the {@code ConnectionMonitor}.
         *
         * @return a new {@code ConnectionMonitor} instance.
         */
        ConnectionMonitor build();

    }

    /**
     * Internal interface that is used for providing additional information for monitoring purposes.
     */
    interface InfoProvider {

        /**
         * @return the correlation id of the monitoring event.
         */
        String getCorrelationId();

        /**
         * @return the timestamp of the monitoring event.
         */
        Instant getTimestamp();

        /**
         * @return the entity ID for which the monitoring event was thrown.
         */
        @Nullable
        EntityId getEntityId();

        /**
         * @return the headers that were part of the message that caused the monitoring event.
         */
        Map<String, String> getHeaders();

        /**
         * @return the payload that was part of the message that caused the monitoring event.
         */
        @Nullable
        String getPayload();

        /**
         * @return whether this info provider has any data.
         */
        boolean isEmpty();

    }

}
