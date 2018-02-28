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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers.CONTENT_TYPE_KEY;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;

/**
 * Does wrap any {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper} and adds content type checks
 * to mapping methods. It allows to override the mappers content type by any content type.
 */
final class ContentTypeRestrictedMessageMapper implements MessageMapper {


    private final MessageMapper delegate;
    @Nullable
    private final String contentTypeOverride;


    private ContentTypeRestrictedMessageMapper(final MessageMapper delegate, @Nullable final String contentTypeOverride) {
        this.delegate = checkNotNull(delegate);
        this.contentTypeOverride = contentTypeOverride;
    }

    /**
     * Enforces content type checking for the mapper
     *
     * @param mapper the mapper
     * @return the wrapped mapper
     */
    public static MessageMapper wrap(final MessageMapper mapper) {
        return new ContentTypeRestrictedMessageMapper(mapper, null);
    }

    /**
     * Enforces content type checking for the mapper and overrides the content type.
     *
     * @param mapper the mapper
     * @param contentTypeOverride the content type substitution
     * @return the wrapped mapper
     */
    public static MessageMapper wrap(final MessageMapper mapper, final String contentTypeOverride) {
        return new ContentTypeRestrictedMessageMapper(mapper, contentTypeOverride);
    }

    @Override
    public Optional<String> getContentType() {
        return Objects.nonNull(contentTypeOverride) ? Optional.of(contentTypeOverride) : delegate.getContentType();
    }

    @Override
    public void configure(final MessageMapperConfiguration configuration) {
        delegate.configure(configuration);
        requireConfiguredContentType();
    }

    @Override
    public Adaptable map(final ExternalMessage message) {
        final String actualContentType = findContentType(message)
                .orElseThrow(() ->
                        MessageMappingFailedException.newBuilder("?")
                                .description("Make sure you specify the 'Content-Type' when sending your message")
                                .build());

        requireMatchingContentType(actualContentType);
        return delegate.map(message);
    }

    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        final String actualContentType = findContentType(adaptable)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Adaptable headers do not contain a value for %s", CONTENT_TYPE_KEY)));

        requireMatchingContentType(actualContentType);
        return delegate.map(adaptable);
    }

    private void requireConfiguredContentType() {
        if (!getContentType().isPresent()) {
            throw new IllegalConfigurationException("Required configuration property missing: '%s'" +
                    MessageMapperConfigurationProperties.CONTENT_TYPE);
        }
    }


    private void requireMatchingContentType(final String actualContentType) {
        final String contentType = getContentType().filter(s -> !s.isEmpty()).orElseThrow(
                () -> new NotYetConfiguredException(String.format(
                        "A matching Content-Type is required, but none configured. Set a Content-Type with the following key in configuration: %s",
                        MessageMapperConfigurationProperties.CONTENT_TYPE))
        );

        if (!contentType.equalsIgnoreCase(actualContentType)) {
            throw new IllegalArgumentException(
                    String.format("Unsupported value for %s: actual='%s', expected='%s'",
                            CONTENT_TYPE_KEY, actualContentType, contentType));
        }
    }


    private static Optional<String> findContentType(final ExternalMessage internalMessage) {
        checkNotNull(internalMessage);
        return internalMessage.findHeaderIgnoreCase(CONTENT_TYPE_KEY);
    }


    private static Optional<String> findContentType(final Adaptable adaptable) {
        checkNotNull(adaptable);
        return adaptable.getHeaders().map(h -> h.entrySet().stream()
                .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null));
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentTypeRestrictedMessageMapper that = (ContentTypeRestrictedMessageMapper) o;
        return Objects.equals(delegate, that.delegate) &&
                Objects.equals(contentTypeOverride, that.contentTypeOverride);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, contentTypeOverride);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegate=" + delegate +
                ", contentTypeOverride=" + contentTypeOverride +
                "]";
    }
}
