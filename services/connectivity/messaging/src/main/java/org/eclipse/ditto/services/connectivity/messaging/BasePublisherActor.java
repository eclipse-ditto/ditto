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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.placeholder.HeadersPlaceholder;
import org.eclipse.ditto.services.models.connectivity.placeholder.PlaceholderFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.PlaceholderFilter;
import org.eclipse.ditto.services.models.connectivity.placeholder.ThingPlaceholder;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();

    protected boolean isResponseOrError(final OutboundSignal.WithExternalMessage outboundSignal) {
        return (outboundSignal.getExternalMessage().isResponse() || outboundSignal.getExternalMessage().isError());
    }

    protected ExternalMessage applyHeaderMapping(final OutboundSignal.WithExternalMessage outbound,
            final Target target) {
        final ExternalMessage message = outbound.getExternalMessage();
        return target.getHeaderMapping().map(mapping -> {
            if (mapping.getMapping().isEmpty()) {
                return message;
            }
            final Map<String, String> originalHeaders = message.getHeaders();
            final Map<String, String> mappedHeaders = new HashMap<>();
            mapping.getMapping().forEach((key, value) -> {
                final String withHeaders = PlaceholderFilter.apply(value, originalHeaders, HEADERS_PLACEHOLDER, true);
                final String withHeadersAndThingId =
                        PlaceholderFilter.apply(withHeaders, outbound.getSource().getId(), THING_PLACEHOLDER);
                mappedHeaders.put(key, withHeadersAndThingId);
            });
            log().debug("Result of header mapping ({}): {}", mapping, mappedHeaders);
            return message.withHeaders(mappedHeaders);
        }).orElse(message);
    }

    protected abstract T toPublishTarget(final String address);

    protected abstract DiagnosticLoggingAdapter log();
}
