/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;

import akka.pattern.AskTimeoutException;

/**
 * Abstract implementation of {@link EnforcementReloaded} providing common functionality of all entity specific
 * enforcement implementations.
 *
 * @param <S> the type of the Signal to enforce/authorize.
 * @param <R> the type of the CommandResponse to filter.
 */
public abstract class AbstractEnforcementReloaded<S extends Signal<?>, R extends CommandResponse<?>>
        implements EnforcementReloaded<S, R> {

    protected static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(AbstractEnforcementReloaded.class);

    @Nullable protected Function<PolicyId, CompletionStage<PolicyEnforcer>> policyEnforcerLoader;
    @Nullable protected Consumer<Policy> policyInjectionConsumer;

    @Override
    public void registerPolicyEnforcerLoader(
            final Function<PolicyId, CompletionStage<PolicyEnforcer>> policyEnforcerLoader) {
        this.policyEnforcerLoader = policyEnforcerLoader;
    }

    @Override
    public void registerPolicyInjectionConsumer(final Consumer<Policy> policyInjectionConsumer) {
        this.policyInjectionConsumer = policyInjectionConsumer;
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, it is returned as is
     * (without modification), otherwise it is wrapped inside a {@link DittoInternalErrorException}.
     *
     * @param hint hint about the nature of the error.
     * @param throwable the error.
     * @param dittoHeaders the DittoHeaders to use for the DittoRuntimeException.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    protected static DittoRuntimeException reportError(final String hint, @Nullable final Throwable throwable,
            final DittoHeaders dittoHeaders) {
        final Throwable error = throwable == null
                ? new NullPointerException("Result and error are both null")
                : throwable;
        final var dre = DittoRuntimeException.asDittoRuntimeException(
                error, cause -> reportUnexpectedError(hint, cause, dittoHeaders));
        LOGGER.withCorrelationId(dittoHeaders)
                .info("{} - {}: {}", hint, dre.getClass().getSimpleName(), dre.getMessage());
        return dre;
    }

    /**
     * Reports an error or a response based on type of the error and whether a response was present or not.
     * If the error is of type {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, it is returned as
     * is (without modification), otherwise it is wrapped inside a {@link DittoInternalErrorException}.
     *
     * @param hint hint about the nature of the error.
     * @param response the (optional) response.
     * @param error the (optional) error.
     * @param dittoHeaders the DittoHeaders to use for the DittoRuntimeException.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    public static DittoRuntimeException reportErrorOrResponse(final String hint, @Nullable final Object response,
            @Nullable final Throwable error, final DittoHeaders dittoHeaders) {

        if (error != null) {
            return reportError(hint, error, dittoHeaders);
        } else if (response instanceof Throwable throwable) {
            return reportError(hint, throwable, dittoHeaders);
        } else if (response != null) {
            return reportUnknownResponse(hint, response, dittoHeaders);
        } else {
            return reportError(hint, new NullPointerException("Response and error were null."), dittoHeaders);
        }
    }

    /**
     * Reports an unknown response as a DittoInternalErrorException.
     *
     * @param hint hint about the nature of the error.
     * @param response the unknown response.
     * @param dittoHeaders the DittoHeaders to use for the DittoRuntimeException.
     * @return DittoInternalErrorException
     */
    protected static DittoRuntimeException reportUnknownResponse(final String hint, final Object response,
            final DittoHeaders dittoHeaders) {
        LOGGER.withCorrelationId(dittoHeaders)
                .error("Unexpected response {}: <{}>", hint, response);

        return DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
    }

    /**
     * Check whether response or error from a future is {@code AskTimeoutException}.
     *
     * @param response response from a future.
     * @param error error thrown in a future.
     * @return whether either is {@code AskTimeoutException}.
     */
    protected static boolean isAskTimeoutException(final Object response, @Nullable final Throwable error) {
        return error instanceof AskTimeoutException || response instanceof AskTimeoutException;
    }

    private static DittoRuntimeException reportUnexpectedError(final String hint, final Throwable error,
            final DittoHeaders dittoHeaders) {
        LOGGER.withCorrelationId(dittoHeaders)
                .error("Unexpected error {} - {}: {}", hint, error.getClass().getSimpleName(),
                        error.getMessage(), error);

        return DittoInternalErrorException.newBuilder()
                .cause(error)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}


