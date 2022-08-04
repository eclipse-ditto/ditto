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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Abstract implementation for mapping one type to another.
 *
 * @param <I> the incoming type.
 * @param <O> the type of the processing result which is passed to the result handler.
 */
abstract class AbstractMappingProcessor<I, O> {

    protected final ThreadSafeDittoLoggingAdapter logger;
    protected final ConnectionId connectionId;
    protected final ConnectionType connectionType;
    protected final ExpressionResolver connectionIdResolver;

    private final MessageMapperRegistry registry;

    protected AbstractMappingProcessor(final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ConnectionId connectionId,
            final ConnectionType connectionType) {

        this.logger = checkNotNull(logger, "logger");
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.connectionType = checkNotNull(connectionType, "connectionType");
        this.registry = checkNotNull(registry, "registry");
        connectionIdResolver = PlaceholderFactory
                .newExpressionResolver(ConnectivityPlaceholders.newConnectionIdPlaceholder(), connectionId);
        logger.info("Configured for processing messages with the following MessageMapperRegistry: <{}>", registry);
    }

    /**
     * Processes an instance of the incoming type {@link I} which results in a list of mapping results of type
     * {@link O}.
     *
     * @param incoming the incoming object.
     * @return the processed result.
     */
    abstract List<MappingOutcome<O>> process(I incoming);

    boolean resolveConditions(final Collection<String> conditions, final ExpressionResolver resolver) {
        boolean conditionBool = true;
        final String templatePattern = "'{{' fn:default(''true'') | {0} '}}'";
        for (final String condition : conditions) {
            final String template = MessageFormat.format(templatePattern, condition);
            final Optional<String> resolvedCondition = PlaceholderFilter.applyOrElseDelete(template, resolver);
            conditionBool &= resolvedCondition.isPresent();
        }
        return conditionBool;
    }

    static MessageMappingFailedException buildMappingFailedException(final String direction,
            final String contentType,
            final String mapperId,
            final DittoHeaders dittoHeaders,
            final Throwable e) {

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

}
