/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.ExpressionResolver;

/**
 * Execute filter for requested-acks configured in connection sources.
 */
final class RequestedAcksFilter {

    private static final String REQUESTED_ACKS_KEY = DittoHeaderDefinition.REQUESTED_ACKS.getKey();

    private RequestedAcksFilter() {
        throw new AssertionError();
    }

    /**
     * Apply the configured requested-acks filter to a signal mapped from an external message.
     * <ul>
     * <li>If the filter is tripped, set requested-acks to []</li>
     * <li>If the filter is not tripped and requested-acks is defined, leave it</li>
     * <li>If the filter is not tripped, requested-acks is not defined, fallback to the resolved value of the filter.</li>
     * </ul>
     *
     * @param signal signal to filter requested acknowledges for
     * @param externalMessage incoming external message that mapped to the signal
     * @param filter the filter string
     * @param connectionId the connection ID receiving the signal.
     * @return the filtered signal.
     */
    static Signal<?> filterAcknowledgements(final Signal<?> signal,
            final ExternalMessage externalMessage,
            @Nullable final String filter,
            final ConnectionId connectionId) {
        if (filter != null) {
            final boolean headerDefined = signal.getDittoHeaders().containsKey(REQUESTED_ACKS_KEY);
            final String requestedAcksValue = getDefaultRequestedAcks(headerDefined, signal);
            final String fullFilter = "fn:default('" + requestedAcksValue + "')|" + filter;
            final ExpressionResolver resolver = Resolvers.forExternalMessage(externalMessage, connectionId);
            final Optional<String> resolverResult = resolver.resolveAsPipelineElement(fullFilter).toOptional();
            if (resolverResult.isEmpty()) {
                // filter tripped: set requested-acks to []
                return signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                        .acknowledgementRequests(Collections.emptySet())
                        .build());
            } else if (headerDefined) {
                // filter not tripped, header defined
                return signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                        .putHeader(REQUESTED_ACKS_KEY, resolverResult.orElseThrow())
                        .build());
            } else {
                // filter not tripped, header not defined:
                // - evaluate filter again against unresolved and set requested-acks accordingly
                // - if filter is not resolved, then keep requested-acks undefined for the default behavior
                final Optional<String> unsetFilterResult =
                        resolver.resolveAsPipelineElement(filter).toOptional();
                return unsetFilterResult.<Signal<?>>map(newAckRequests ->
                        signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                                .putHeader(REQUESTED_ACKS_KEY, newAckRequests)
                                .build()))
                        .orElse(signal);
            }
        }
        return signal;
    }

    private static String getDefaultRequestedAcks(final boolean headerDefined, final Signal<?> signal) {
        if (headerDefined) {
            final String headerValue = signal.getDittoHeaders().get(REQUESTED_ACKS_KEY);
            if (!headerValue.contains("'")) {
                return headerValue;
            }
        }
        return "[]";
    }
}
