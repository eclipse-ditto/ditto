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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.signals.base.Signal;

import akka.stream.javadsl.Source;

/**
 * {@link MappingResultHandler} for inbound messages. This handler forwards to the given handlers. Additionally it
 * calls the {@link MappingResultHandler#onException(Exception)} method for exceptions thrown in handlers and
 * increases the according counters for mapped, dropped failed messages.
 *
 * @param <R> type of results.
 */
final class InboundMappingResultHandler<R> implements MappingResultHandler<MappedInboundExternalMessage, R> {

    private final Function<MappedInboundExternalMessage, R> onMessageMapped;
    private final Runnable onMessageDropped;
    private final Consumer<Exception> onException;
    private final R emptyResult;
    private final BinaryOperator<R> combineResults;
    private final ConnectionMonitor inboundMapped;
    private final ConnectionMonitor inboundDropped;
    private final ConnectionMonitor.InfoProvider infoProvider;

    private InboundMappingResultHandler(final Builder<R> builder) {
        onMessageMapped = checkNotNull(builder.onMessageMapped, "onMessageMapped");
        onMessageDropped = checkNotNull(builder.onMessageDropped, "onMessageDropped");
        onException = checkNotNull(builder.onException, "onException");
        inboundMapped = checkNotNull(builder.inboundMapped, "inboundMapped");
        inboundDropped = checkNotNull(builder.inboundDropped, "inboundDropped");
        infoProvider = checkNotNull(builder.infoProvider, "infoProvider");
        emptyResult = checkNotNull(builder.emptyResult, "emptyResult");
        combineResults = checkNotNull(builder.combineResults, "combineResults");
    }

    static <S> Builder<Source<S, ?>> newSourceBuilder() {
        return new Builder<Source<S, ?>>()
                .emptyResult(Source.empty())
                .combineResults(Source::concat);
    }

    @Override
    public R onMessageMapped(final MappedInboundExternalMessage inboundExternalMessage) {
        try {
            inboundMapped.success(infoProvider);
            return onMessageMapped.apply(inboundExternalMessage);
        } catch (final Exception e) {
            return onException(e);
        }
    }

    @Override
    public R onMessageDropped() {
        try {
            inboundDropped.success(infoProvider);
            onMessageDropped.run();
            return emptyResult;
        } catch (Exception e) {
            return onException(e);
        }
    }

    @Override
    public R onException(final Exception exception) {
        if (exception instanceof DittoRuntimeException) {
            inboundMapped.failure(((DittoRuntimeException) exception));
        } else {
            inboundMapped.exception(exception);
        }
        onException.accept(exception);
        return emptyResult;
    }

    @Override
    public R combineResults(final R left, final R right) {
        return combineResults.apply(left, right);
    }

    @Override
    public R emptyResult() {
        return emptyResult;
    }

    static final class Builder<R> {

        private ConnectionMonitor inboundMapped;
        private ConnectionMonitor inboundDropped;
        private ConnectionMonitor.InfoProvider infoProvider;
        private Function<MappedInboundExternalMessage, R> onMessageMapped;
        private Runnable onMessageDropped;
        private Consumer<Exception> onException;
        private R emptyResult;
        private BinaryOperator<R> combineResults;

        private Builder() {}

        InboundMappingResultHandler<R> build() {
            return new InboundMappingResultHandler<>(this);
        }

        Builder<R> inboundMapped(final ConnectionMonitor inboundMapped) {
            this.inboundMapped = inboundMapped;
            return this;
        }

        Builder<R> inboundDropped(final ConnectionMonitor inboundDropped) {
            this.inboundDropped = inboundDropped;
            return this;
        }

        Builder<R> infoProvider(final ConnectionMonitor.InfoProvider infoProvider) {
            this.infoProvider = infoProvider;
            return this;
        }

        Builder<R> onMessageMapped(
                final Function<MappedInboundExternalMessage, R> onMessageMapped) {
            this.onMessageMapped = onMessageMapped;
            return this;
        }

        Builder<R> onMessageDropped(final Runnable onMessageDropped) {
            this.onMessageDropped = onMessageDropped;
            return this;
        }

        Builder<R> onException(final Consumer<Exception> onException) {
            this.onException = onException;
            return this;
        }

        Builder<R> emptyResult(final R emptyResult) {
            this.emptyResult = emptyResult;
            return this;
        }

        Builder<R> combineResults(final BinaryOperator<R> combineResults) {
            this.combineResults = combineResults;
            return this;
        }
    }

}
