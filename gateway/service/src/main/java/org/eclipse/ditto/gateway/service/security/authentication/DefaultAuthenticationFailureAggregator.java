/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authentication;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;

/**
 * Default implementation of {@link AuthenticationFailureAggregator}.
 */
@Immutable
final class DefaultAuthenticationFailureAggregator implements AuthenticationFailureAggregator {

    private static final String AGGREGATED_AUTH_FAILURE_MESSAGE =
            "Multiple authentication mechanisms were applicable but none succeeded.";

    private static final String AGGREGATED_AUTH_FAILURE_DESCRIPTION_PREFIX =
            "For a successful authentication see the following suggestions: ";

    private static final DefaultAuthenticationFailureAggregator INSTANCE = new DefaultAuthenticationFailureAggregator();

    private DefaultAuthenticationFailureAggregator() {
        super();
    }

    static DefaultAuthenticationFailureAggregator getInstance() {
        return INSTANCE;
    }

    @Override
    public DittoRuntimeException aggregateAuthenticationFailures(final List<AuthenticationResult> failedAuthResults) {
        final List<DittoRuntimeException> reasonsOfFailure =
                getDittoRuntimeExceptionReasonsWithDescription(failedAuthResults);

        if (reasonsOfFailure.isEmpty()) {
            final String msgPattern = "The failed authentication results did not contain any failure reason of type " +
                    "<{0}> containing a description!";
            final String message = MessageFormat.format(msgPattern, DittoRuntimeException.class.getSimpleName());
            throw new IllegalArgumentException(message);
        }

        if (1 == reasonsOfFailure.size()) {
            return reasonsOfFailure.get(0);
        }

        return reasonsOfFailure.stream()
                .filter(reasonOfFailure -> !HttpStatus.UNAUTHORIZED.equals(reasonOfFailure.getHttpStatus()))
                .findFirst()
                .orElseGet(() -> GatewayAuthenticationFailedException.newBuilder(AGGREGATED_AUTH_FAILURE_MESSAGE)
                        .description(buildAggregatedDescriptionFromDittoRuntimeExceptions(reasonsOfFailure))
                        .dittoHeaders(buildAggregatedHeaders(reasonsOfFailure))
                        .build());
    }

    private static List<DittoRuntimeException> getDittoRuntimeExceptionReasonsWithDescription(
            final Collection<AuthenticationResult> failedAuthenticationResults) {

        return failedAuthenticationResults.stream()
                .map(AuthenticationResult::getReasonOfFailure)
                .map(DefaultAuthenticationFailureAggregator::toDittoRuntimeException)
                .filter(Objects::nonNull)
                .filter(reasonOfFailure -> reasonOfFailure.getDescription().isPresent())
                .toList();
    }

    @Nullable
    private static DittoRuntimeException toDittoRuntimeException(final Throwable throwable) {
        if (throwable instanceof DittoRuntimeException dittoRuntimeException) {
            return dittoRuntimeException;
        } else if (null == throwable) {
            return null;
        } else {
            return toDittoRuntimeException(throwable.getCause());
        }
    }

    private static String buildAggregatedDescriptionFromDittoRuntimeExceptions(
            final Collection<DittoRuntimeException> reasonsOfFailure) {

        final Function<Optional<String>, String> toDescriptionString =
                optional -> optional.map(description -> "{ " + description + " }").orElse("");

        final String reasonDescriptions = reasonsOfFailure.stream()
                .map(DittoRuntimeException::getDescription)
                .map(toDescriptionString)
                .collect(Collectors.joining(", "));

        return AGGREGATED_AUTH_FAILURE_DESCRIPTION_PREFIX + reasonDescriptions + ".";
    }

    private static DittoHeaders buildAggregatedHeaders(final Iterable<DittoRuntimeException> reasonsOfFailure) {
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder();
        for (final DittoRuntimeException reasonOfFailure : reasonsOfFailure) {
            dittoHeadersBuilder.putHeaders(reasonOfFailure.getDittoHeaders());
        }
        return dittoHeadersBuilder.build();
    }

}
