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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Applies an optional "header mapping" potentially configured on a target on a passed {@link OutboundSignal.Mapped}.
 *
 * @since 1.3.0
 */
@Immutable
final class OutboundSignalToExternalMessage implements Supplier<ExternalMessage> {

    private final OutboundSignal.Mapped outboundMappedSignal;
    private final ExpressionResolver expressionResolver;
    private final Map<String, String> headerMapping;
    private final ThreadSafeDittoLogger logger;

    private OutboundSignalToExternalMessage(final OutboundSignal.Mapped outboundMappedSignal,
            final ExpressionResolver expressionResolver, final Map<String, String> headerMapping) {

        this.outboundMappedSignal = checkNotNull(outboundMappedSignal, "outboundMappedSignal");
        this.expressionResolver = checkNotNull(expressionResolver, "expressionResolver");
        this.headerMapping = headerMapping;
        logger = DittoLoggerFactory.getThreadSafeLogger(getClass()).withCorrelationId(outboundMappedSignal.getSource());
    }

    /**
     * Returns a new instance of OutboundSignalToExternalMessage.
     *
     * @param outboundMappedSignal the OutboundSignal containing the {@link ExternalMessage} with headers potentially
     * containing placeholders
     * @param expressionResolver the expression-resolver used to resolve placeholders and optionally pipeline stages
     * (functions).
     * @param headerMapping optional header mappings to apply.
     * @return the instance.
     * @throws NullPointerException if {@code outboundMappedSignal} or {@code expressionResolver} is {@code null}.
     */
    static OutboundSignalToExternalMessage newInstance(final OutboundSignal.Mapped outboundMappedSignal,
            final ExpressionResolver expressionResolver, @Nullable final HeaderMapping headerMapping) {

        final Map<String, String> mapping;
        if (null != headerMapping) {
            mapping = headerMapping.getMapping();
        } else {
            mapping = Map.of();
        }
        return new OutboundSignalToExternalMessage(outboundMappedSignal, expressionResolver, mapping);
    }

    @Override
    public ExternalMessage get() {
        final ExternalMessage originalMessage = outboundMappedSignal.getExternalMessage();

        final ExternalMessage result;
        if (headerMapping.isEmpty()) {
            result = originalMessage;
        } else {
            result = ExternalMessageFactory.newExternalMessageBuilder(originalMessage)
                    .withAdditionalHeaders(mapHeaders())
                    .build();
        }
        return result;
    }

    private Map<String, String> mapHeaders() {
        final Map<String, String> result = new HashMap<>();
        headerMapping.forEach(
                (key, value) -> mapHeaderByResolver(value).ifPresent(resolvedValue -> result.put(key, resolvedValue)));
        if (logger.isDebugEnabled()) {
            logger.debug("Result of header mapping <{}> are these headers to be published: {}", headerMapping, result);
        }
        return result;
    }

    private Optional<String> mapHeaderByResolver(final String value) {
        return PlaceholderFilter.applyOrElseDelete(value, expressionResolver);
    }

}
