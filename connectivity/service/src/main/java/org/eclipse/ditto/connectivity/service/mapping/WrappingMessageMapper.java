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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MapperLimitsConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;

import akka.actor.ActorSystem;

/**
 * Enforce message size limits on a {@link MessageMapper} and adds random correlation IDs should they not be present
 * in the mapped message.
 */
final class WrappingMessageMapper implements MessageMapper {

    private int inboundMessageLimit;
    private int outboundMessageLimit;

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

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getAlias() {
        return delegate.getAlias();
    }

    @Override
    public boolean isConfigurationMandatory() {
        return delegate.isConfigurationMandatory();
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return delegate.createNewMapperInstance();
    }

    @Override
    public Collection<String> getContentTypeBlocklist() {
        return delegate.getContentTypeBlocklist();
    }

    @Override
    public JsonObject getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return delegate.getIncomingConditions();
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return delegate.getOutgoingConditions();
    }

    /**
     * @return the MessageMapper delegate this instance wraps.
     */
    public MessageMapper getDelegate() {
        return delegate;
    }

    @Override
    public void configure(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final MessageMapperConfiguration configuration,
            final ActorSystem actorSystem) {
        final MapperLimitsConfig mapperLimitsConfig = connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        inboundMessageLimit = mapperLimitsConfig.getMaxMappedInboundMessages();
        outboundMessageLimit = mapperLimitsConfig.getMaxMappedOutboundMessages();
        delegate.configure(connection, connectivityConfig, configuration, actorSystem);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        return checkMaxMappedMessagesLimit(delegate.map(message), inboundMessageLimit, message.getInternalHeaders());
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return delegate.getAdditionalInboundHeaders(message);
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        final var externalMessages = delegate.map(adaptable);
        checkMaxMappedMessagesLimit(externalMessages, outboundMessageLimit, adaptable.getDittoHeaders());

        final var isResponse = AbstractMessageMapper.isResponse(adaptable);
        final UnaryOperator<ExternalMessage> markAsResponse =
                externalMessage -> ExternalMessageFactory.newExternalMessageBuilder(externalMessage)
                        .asResponse(isResponse)
                        .build();

        return externalMessages.stream()
                .map(markAsResponse)
                .toList();
    }

    private <T> List<T> checkMaxMappedMessagesLimit(final List<T> mappingResult, final int maxMappedMessages,
            final DittoHeaders dittoHeaders) {

        if (mappingResult.size() > maxMappedMessages) {
            final var descFormat = "The payload mapping '%s' produced %d messages, which exceeds the limit of %d.";
            throw MessageMappingFailedException.newBuilder(null)
                    .message("The number of messages produced by the payload mapping exceeded the limits.")
                    .dittoHeaders(dittoHeaders)
                    .description(String.format(descFormat, getId(), mappingResult.size(), maxMappedMessages))
                    .build();
        }
        return mappingResult;
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
