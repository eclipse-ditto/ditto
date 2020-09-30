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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;

abstract class AbstractMappingProcessor<I, O> {

    private final MessageMapperRegistry registry;
    private final DittoDiagnosticLoggingAdapter logger;
    private final ConnectionId connectionId;

    protected AbstractMappingProcessor(final MessageMapperRegistry registry,
            final DittoDiagnosticLoggingAdapter logger,
            final ConnectionId connectionId) {

        this.registry = registry;
        this.logger = logger;
        this.connectionId = connectionId;
        logger.info("Configured for processing messages with the following MessageMapperRegistry: <{}>", registry);
    }

    abstract <R> R process(final I incoming, final MappingResultHandler<O, R> resultHandler);

    boolean resolveConditions(final Collection<String> conditions, final ExpressionResolver resolver) {
        boolean conditionBool = true;
        for (String condition : conditions) {
            final String template = "{{ fn:default('true') | " + condition + " }}";
            final String resolvedCondition =
                    PlaceholderFilter.applyOrElseDelete(template, resolver).orElse("false");
            conditionBool &= Boolean.parseBoolean(resolvedCondition);
        }
        return conditionBool;
    }

    static MessageMappingFailedException buildMappingFailedException(final String direction,
            final String contentType,
            final String mapperId,
            final DittoHeaders dittoHeaders,
            final Exception e) {

        final String description =
                String.format("Could not map %s message with mapper '%s' due to unknown problem: %s %s",
                        direction, mapperId, e.getClass().getSimpleName(), e.getMessage());
        return MessageMappingFailedException.newBuilder(contentType)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .cause(e)
                .build();
    }

    List<MessageMapper> getMappers(@Nullable final PayloadMapping payloadMapping) {
        final List<MessageMapper> mappers =
                payloadMapping == null ? Collections.emptyList() : registry.getMappers(payloadMapping);
        if (mappers.isEmpty()) {
            logger.debug("Falling back to default MessageMapper for mapping as no MessageMapper was present.");
            return Collections.singletonList(registry.getDefaultMapper());
        } else {
            return mappers;
        }
    }

    void enhanceLogFromAdaptable(final Adaptable adaptable) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(logger, adaptable, connectionId);
    }

}
