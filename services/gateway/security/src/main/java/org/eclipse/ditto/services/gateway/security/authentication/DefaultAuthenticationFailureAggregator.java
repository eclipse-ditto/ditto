/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.security.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;

/**
 * Default implementation of {@link AuthenticationFailureAggregator}.
 */
@Immutable
public final class DefaultAuthenticationFailureAggregator implements AuthenticationFailureAggregator {

    private static final String AGGREGATED_AUTHENTICATION_FAILURE_MESSAGE =
            "Multiple authentication mechanisms were applicable but none succeeded.";

    private static final String AGGREGATED_AUTHENTICATION_FAILURE_DESCRIPTION_PREFIX =
            "For a successful authentication see the following suggestions: ";

    private DefaultAuthenticationFailureAggregator() {
    }

    static DefaultAuthenticationFailureAggregator getInstance() {
        return new DefaultAuthenticationFailureAggregator();
    }

    /**
     * Aggregates reasons for failure of the given failed authentication results to a single
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException ditto runtime exception}.
     *
     * @param failedAuthenticationResults the list of failed authentication results to aggregate. Must not be empty.
     * Reasons of failure that are no DittoRuntimeExceptions and do not have a Cause of DittoRuntimeException will be
     * ignored.
     * Reasons of failure that does not contain a description will be ignored.
     * @return the exception with the aggregated failure information.
     * @throws java.lang.IllegalArgumentException if the given list of failed authentication results is empty.
     * @throws java.lang.IllegalArgumentException if the given list of failed authentication does not contain any
     * reason of failure of type {@link DittoRuntimeException} containing a
     * {@link DittoRuntimeException#description description}.
     */
    @Override
    public DittoRuntimeException aggregateAuthenticationFailures(
            final List<AuthenticationResult> failedAuthenticationResults) {

        final List<DittoRuntimeException> reasonsOfFailure =
                getDittoRuntimeExceptionReasonsWithDescription(failedAuthenticationResults);

        if (reasonsOfFailure.isEmpty()) {
            throw new IllegalArgumentException(String.format("The passed failed authentication results did not " +
                            "contain any reason of failure of type '%s' containing a description.",
                    DittoRuntimeException.class.getSimpleName()));
        }

        if (reasonsOfFailure.size() == 1) {
            return reasonsOfFailure.get(0);
        }

        return reasonsOfFailure.stream()
                .filter(reasonOfFailure -> !HttpStatusCode.UNAUTHORIZED.equals(reasonOfFailure.getStatusCode()))
                .findFirst()
                .orElse(aggregateToGatewayAuthenticationFailureException(reasonsOfFailure));
    }

    private DittoRuntimeException aggregateToGatewayAuthenticationFailureException(
            final List<DittoRuntimeException> reasonsOfFailure) {
        return GatewayAuthenticationFailedException.newBuilder(AGGREGATED_AUTHENTICATION_FAILURE_MESSAGE)
                .description(buildAggregatedDescriptionFromDittoRuntimeExceptions(reasonsOfFailure))
                .dittoHeaders(buildAggregatedHeaders(reasonsOfFailure))
                .build();
    }

    private List<DittoRuntimeException> getDittoRuntimeExceptionReasonsWithDescription(
            final List<AuthenticationResult> failedAuthenticationResults) {
        final List<DittoRuntimeException> reasonsOfFailure = new ArrayList<>();

        for (AuthenticationResult failedAuthenticationResult : failedAuthenticationResults) {
            final Optional<DittoRuntimeException> reasonOfFailureOptional =
                    toDittoRuntimeException(failedAuthenticationResult.getReasonOfFailure());

            if (!reasonOfFailureOptional.isPresent()) {
                continue;
            }

            final DittoRuntimeException reasonOfFailure = reasonOfFailureOptional.get();

            if (reasonOfFailure.getDescription().isPresent()) {
                reasonsOfFailure.add(reasonOfFailure);
            }
        }

        return reasonsOfFailure;
    }

    private String buildAggregatedDescriptionFromDittoRuntimeExceptions(
            final List<DittoRuntimeException> reasonsOfFailure) {
        final StringBuilder aggregatedDescription =
                new StringBuilder(AGGREGATED_AUTHENTICATION_FAILURE_DESCRIPTION_PREFIX);

        final ListIterator<DittoRuntimeException> iterator = reasonsOfFailure.listIterator();
        while (iterator.hasNext()) {
            final DittoRuntimeException reasonOfFailure = iterator.next();
            final Optional<String> description = reasonOfFailure.getDescription();
            if (description.isPresent()) {
                aggregatedDescription.append("{ ");
                aggregatedDescription.append(description.get());
                aggregatedDescription.append(" }");
                if (iterator.hasNext()) {
                    aggregatedDescription.append(", ");
                }
            }
        }

        aggregatedDescription.append(".");

        return aggregatedDescription.toString();
    }

    private DittoHeaders buildAggregatedHeaders(final List<DittoRuntimeException> reasonsOfFailure) {
        final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder();
        for (DittoRuntimeException reasonOfFailure : reasonsOfFailure) {
            dittoHeadersBuilder.putHeaders(reasonOfFailure.getDittoHeaders());
        }
        return dittoHeadersBuilder.build();
    }

    private Optional<DittoRuntimeException> toDittoRuntimeException(final Throwable throwable) {
        if (throwable == null) {
            return Optional.empty();
        }

        if (throwable instanceof DittoRuntimeException) {
            return Optional.of((DittoRuntimeException) throwable);
        }

        return toDittoRuntimeException(throwable.getCause());
    }
}
