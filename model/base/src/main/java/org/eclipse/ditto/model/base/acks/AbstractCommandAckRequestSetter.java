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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * This UnaryOperator sets the headers of response-required and requested-acknowledgements in a command
 * unless already defined.
 * <p>
 * Unless already defined, the header requested-acks will be an implicit acknowledgement when response-required=true
 * and an empty JSON array when response-required=false.
 * <p>
 * Unless already defined, the header response-required is true unless requested-acks is defined and empty or
 * timeout is defined and zero.
 * <p>
 * These default values are set in parallel. The default value of one does not affect the default value of the other.
 *
 * @param <C> the type of the command which should be enhanced with implicit acknowledgement labels.
 * @since 1.2.0
 */
@Immutable
public abstract class AbstractCommandAckRequestSetter<C extends WithDittoHeaders> implements UnaryOperator<C> {

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
    @SuppressWarnings("unchecked")
    public C apply(final C command) {
        checkNotNull(command, "command");
        if (isApplicable(command)) {
            return (C) setDefaultDittoHeaders(command.getDittoHeaders())
                    .map(command::setDittoHeaders)
                    .orElse(command);
        } else {
            return command;
        }
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
     * Get the class of the type of commands this handles.
     *
     * @return the class of the command.
     */
    public abstract Class<C> getMatchedClass();

    /**
     * Determines whether the passed {@code command} was sent via the "live" channel.
     *
     * @param command the command to check
     * @return whether the command was sent via the "live" channel.
     */
    protected boolean isLiveChannelCommand(final C command) {
        return command.getDittoHeaders().getChannel().filter(LIVE_CHANNEL::equals).isPresent();
    }

    private Optional<DittoHeaders> setDefaultDittoHeaders(final DittoHeaders headers) {
        final DittoHeadersBuilder<?, ?> builder = headers.toBuilder();
        final Set<AcknowledgementRequest> requestedAcks = headers.getAcknowledgementRequests();
        // set acks-requested and/or response-required according to other headers, always
        final boolean implicitAcksRequested = implicitAcksRequested(builder, headers);
        final boolean explicitRequestedAcksFiltered = explicitRequestedAcksFiltered(builder, requestedAcks);
        final boolean responseRequiredSet = responseRequiredSet(headers, builder);
        // if any modification exists, return the new headers
        if (implicitAcksRequested || explicitRequestedAcksFiltered || responseRequiredSet) {
            return Optional.of(builder.build());
        } else {
            return Optional.empty();
        }
    }

    private boolean implicitAcksRequested(final DittoHeadersBuilder<?, ?> builder, final DittoHeaders headers) {
        if (!headers.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey())) {
            if (headers.isResponseRequired()) {
                builder.acknowledgementRequest(AcknowledgementRequest.of(implicitAcknowledgementLabel));
            } else {
                builder.acknowledgementRequests(Collections.emptySet());
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean explicitRequestedAcksFiltered(final DittoHeadersBuilder<?, ?> builder,
            final Set<AcknowledgementRequest> requestedAcks) {

        if (!requestedAcks.isEmpty()) {
            final Set<AcknowledgementRequest> filteredAckRequests = requestedAcks.stream()
                    .filter(request -> !negatedDittoAcknowledgementLabels.contains(request.getLabel()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            final boolean isFiltered = filteredAckRequests.size() != requestedAcks.size();
            if (isFiltered) {
                builder.acknowledgementRequests(filteredAckRequests);
            }
            return isFiltered;
        } else {
            return false;
        }
    }

    private boolean responseRequiredSet(final DittoHeaders dittoHeaders, final DittoHeadersBuilder<?, ?> builder) {
        if (!dittoHeaders.containsKey(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey())) {
            final boolean areAcksExplicit = dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
            final boolean areAcksEffective = dittoHeaders.getAcknowledgementRequests()
                    .stream()
                    .map(AcknowledgementRequest::getLabel)
                    .anyMatch(label -> !negatedDittoAcknowledgementLabels.contains(label));
            final boolean hasEmptyRequestedAcks = areAcksExplicit && !areAcksEffective;
            final boolean hasTimeoutZero = dittoHeaders.getTimeout().filter(Duration::isZero).isPresent();
            final boolean isResponseRequired = !(hasEmptyRequestedAcks || hasTimeoutZero);
            builder.responseRequired(isResponseRequired);
            return true;
        } else {
            return false;
        }
    }

}
