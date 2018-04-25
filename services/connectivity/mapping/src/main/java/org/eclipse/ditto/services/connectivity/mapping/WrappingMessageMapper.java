/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

import com.typesafe.config.Config;

/**
 * Does wrap any {@link MessageMapper}.
 * <p>
 * adds headers to ExternalMessage and Adaptable in mappings even when the wrapped {@link MessageMapper} does
 * forget to do so by himself.
 * </p>
 */
final class WrappingMessageMapper implements MessageMapper {


    private final MessageMapper delegate;

    private WrappingMessageMapper(final MessageMapper delegate) {
        this.delegate = checkNotNull(delegate);
    }

    /**
     * Enforces content type checking for the mapper
     *
     * @param mapper the mapper
     * @return the wrapped mapper
     */
    public static MessageMapper wrap(final MessageMapper mapper) {
        return new WrappingMessageMapper(mapper);
    }

    /**
     * @return the MessageMapper delegate this instance wraps.
     */
    public MessageMapper getDelegate() {
        return delegate;
    }

    @Override
    public void configure(final Config mappingConfig, final MessageMapperConfiguration configuration) {
        delegate.configure(mappingConfig, configuration);
    }

    @Override
    public Optional<Adaptable> map(final ExternalMessage message) {

        final ExternalMessage enhancedMessage;
        final String correlationId;
        if (!message.getHeaders().containsKey(DittoHeaderDefinition.CORRELATION_ID.getKey())) {
            // if no correlation-id was provided in the ExternalMessage, generate one here:
            correlationId = UUID.randomUUID().toString();
            enhancedMessage = ConnectivityModelFactory.newExternalMessageBuilder(message)
                    .withAdditionalHeaders(DittoHeaderDefinition.CORRELATION_ID.getKey(),
                            correlationId)
                    .build();
        } else {
            correlationId = message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
            enhancedMessage = message;
        }

        final Optional<Adaptable> mappedOpt = delegate.map(enhancedMessage);

        return mappedOpt.map(mapped -> {
            final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder();
            headersBuilder.correlationId(correlationId);

            Optional.ofNullable(message.getHeaders().get(ExternalMessage.REPLY_TO_HEADER)).ifPresent(replyTo ->
                    headersBuilder.putHeader(ExternalMessage.REPLY_TO_HEADER, replyTo)
            );

            final Optional<DittoHeaders> headersOpt = mapped.getHeaders();
            headersOpt.ifPresent(headersBuilder::putHeaders); // overwrite with mapped headers (if any)

            return ProtocolFactory.newAdaptableBuilder(mapped)
                    .withHeaders(headersBuilder.build())
                    .build();
        });
    }

    @Override
    public Optional<ExternalMessage> map(final Adaptable adaptable) {

        final Optional<ExternalMessage> mappedOpt = delegate.map(adaptable);

        return mappedOpt.map(mapped -> {
            final ExternalMessageBuilder messageBuilder = ConnectivityModelFactory.newExternalMessageBuilder(mapped);
            messageBuilder.asResponse(adaptable.getPayload().getStatus().isPresent());
            adaptable.getHeaders()
                    .map(h -> h.get(ExternalMessage.REPLY_TO_HEADER))
                    .ifPresent(
                            replyTo -> messageBuilder.withAdditionalHeaders(ExternalMessage.REPLY_TO_HEADER, replyTo));
            return messageBuilder.build();
        });
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WrappingMessageMapper that = (WrappingMessageMapper) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegate=" + delegate +
                "]";
    }
}
