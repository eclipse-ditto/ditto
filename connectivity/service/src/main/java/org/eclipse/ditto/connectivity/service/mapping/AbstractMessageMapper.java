/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Abstract implementation of {@link MessageMapper} which adds an id field and also its initialization from mapping
 * configuration (id is not passed as constructor argument because the mappers are created by reflection).
 */
public abstract class AbstractMessageMapper implements MessageMapper {

    protected final ActorSystem actorSystem;
    protected final Config config;

    private String id;
    private Map<String, String> incomingConditions;
    private Map<String, String> outgoingConditions;
    private Collection<String> contentTypeBlocklist;

    protected AbstractMessageMapper(final ActorSystem actorSystem, final Config config) {
        this.actorSystem = actorSystem;
        this.config = config;
    }

    protected AbstractMessageMapper(final AbstractMessageMapper copyFromMapper) {
        this.actorSystem = copyFromMapper.actorSystem;
        this.config = copyFromMapper.config;
        this.id = copyFromMapper.getId();
        this.incomingConditions = copyFromMapper.getIncomingConditions();
        this.outgoingConditions = copyFromMapper.getOutgoingConditions();
        this.contentTypeBlocklist = copyFromMapper.getContentTypeBlocklist();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return incomingConditions;
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return outgoingConditions;
    }

    @Override
    public Collection<String> getContentTypeBlocklist() {
        return contentTypeBlocklist;
    }

    @Override
    public final void configure(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final MessageMapperConfiguration configuration,
            final ActorSystem actorSystem) {

        this.id = configuration.getId();
        this.incomingConditions = configuration.getIncomingConditions();
        this.outgoingConditions = configuration.getOutgoingConditions();
        this.contentTypeBlocklist = configuration.getContentTypeBlocklist();
        final MappingConfig mappingConfig = connectivityConfig.getMappingConfig();
        doConfigure(connection, mappingConfig, configuration);
    }

    /**
     * Applies the mapper specific configuration.
     *
     * @param connection the connection to apply the mapping for.
     * @param mappingConfig the service configuration for the mapping.
     * @param configuration the mapper specific configuration configured in scope of a single connection.
     */
    protected void doConfigure(final Connection connection, final MappingConfig mappingConfig,
            final MessageMapperConfiguration configuration) {
        // noop default
    }

    /**
     * Extracts the payload of the passed in {@code message} as string.
     *
     * @param message the external message to extract the payload from.
     * @return the payload of the passed in {@code message} as string
     * @throws MessageMappingFailedException if no payload was present or if it was empty.
     */
    protected static String extractPayloadAsString(final ExternalMessage message) {
        final Optional<String> payload;
        if (message.isTextMessage()) {
            payload = message.getTextPayload();
        } else if (message.isBytesMessage()) {
            final Charset charset = determineCharset(message.getHeaders());
            payload = message.getBytePayload().map(charset::decode).map(CharBuffer::toString);
        } else {
            payload = Optional.empty();
        }

        return payload.filter(s -> !s.isEmpty()).orElseThrow(() ->
                MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                        .description(
                                "As payload was absent or empty, please make sure to send payload in your messages.")
                        .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                        .build());
    }

    protected static Charset determineCharset(final Map<String, String> messageHeaders) {
        return CharsetDeterminer.getInstance().apply(messageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER));
    }

    protected static boolean isResponse(final Adaptable adaptable) {
        final var payload = adaptable.getPayload();
        final var httpStatus = payload.getHttpStatus();
        return httpStatus.isPresent();
    }

    protected static boolean isError(final Adaptable adaptable) {
        final var topicPath = adaptable.getTopicPath();
        return topicPath.isCriterion(TopicPath.Criterion.ERRORS);
    }

    protected static boolean isLiveSignal(final Adaptable adaptable) {
        return adaptable.getTopicPath().isChannel(TopicPath.Channel.LIVE);
    }

    @Override
    public String toString() {
        return "id=" + id +
            ", incomingConditions=" + incomingConditions +
            ", outgoingConditions=" + outgoingConditions +
            ", contentTypeBlocklist=" + contentTypeBlocklist;
    }
}
