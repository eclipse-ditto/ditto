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
package org.eclipse.ditto.model.base.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * This UnaryOperator accepts a Command and checks whether its DittoHeaders should be extended by an
 * {@link AcknowledgementRequest} for a configured {@code implicitAcknowledgementLabel}.
 * <p>
 * If so, the result is a new command with extended headers, else the same command is returned.
 * The headers are only extended if the command is {@link #isApplicable(WithDittoHeaders)},
 * {@link DittoHeaders#isResponseRequired()} evaluates to {@code true} and if command headers do not yet contain
 * acknowledgement requests.
 * </p>
 *
 * @param <C> the type of the command which should be enhanced with implicit acknowledgement labels.
 * @since 1.2.0
 */
@Immutable
public abstract class AbstractCommandAckRequestSetter<C extends WithDittoHeaders<?>> implements UnaryOperator<C> {

    private static final String LIVE_CHANNEL = "live";

    private final AcknowledgementLabel implicitAcknowledgementLabel;
    private final Set<AcknowledgementLabel> negatedDittoAcknowledgementLabels;

    protected AbstractCommandAckRequestSetter(final AcknowledgementLabel implicitAcknowledgementLabel) {
        negatedDittoAcknowledgementLabels = Collections.unmodifiableSet(
                Arrays.stream(DittoAcknowledgementLabel.values())
                .filter(v -> !implicitAcknowledgementLabel.equals(v))
                .collect(Collectors.toSet()));
        this.implicitAcknowledgementLabel = implicitAcknowledgementLabel;
    }

    @Override
    public C apply(final C command) {
        C result = checkNotNull(command, "command");
        if (isResponseRequired(command) && isApplicable(command)) {
            result = requestImplicitAckLabelIfNoOtherAcksAreRequested(command);
        }
        return result;
    }

    private boolean isResponseRequired(final C command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    /**
     * Checks if the passed {@code command} is applicable for adding the implicit acknowledgement label.
     *
     * @param command the command to check.
     * @return whether the command is applicable for adding the implicit acknowledgement label.
     * @throws NullPointerException if the passed {@code command} was {@code null}.
     */
    public abstract boolean isApplicable(C command);

    /**
     * Determines whether the passed {@code command} was sent via the "live" channel.
     *
     * @param command the command to check
     * @return whether the command was sent via the "live" channel.
     */
    protected boolean isLiveChannelCommand(final C command) {
        return command.getDittoHeaders().getChannel().filter(LIVE_CHANNEL::equals).isPresent();
    }

    private C requestImplicitAckLabelIfNoOtherAcksAreRequested(final C command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<AcknowledgementRequest> ackRequests = dittoHeaders.getAcknowledgementRequests();
        final boolean requestedAcksHeaderPresent =
                dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());

        if (ackRequests.isEmpty() && !requestedAcksHeaderPresent) {
            return insertAcknowledgementRequestsToHeaders(command, dittoHeaders,
                    Collections.singleton(AcknowledgementRequest.of(implicitAcknowledgementLabel)));
        } else if (!ackRequests.isEmpty()) {
            final Set<AcknowledgementRequest> filteredAckRequests = ackRequests.stream()
                    .filter(request -> !negatedDittoAcknowledgementLabels.contains(request.getLabel()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return insertAcknowledgementRequestsToHeaders(command, dittoHeaders, filteredAckRequests);
        }
        return command;
    }

    @SuppressWarnings("unchecked")
    private C insertAcknowledgementRequestsToHeaders(final C command, final DittoHeaders dittoHeaders,
            final Set<AcknowledgementRequest> newAckRequests) {

        return (C) command.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequests(newAckRequests)
                .build());
    }

}
