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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

class ContentTypeRestrictedMessageMapper implements MessageMapper {

    public static final String CONTENT_TYPE_KEY = "Content-Type";

    private final MessageMapper delegate;
    @Nullable
    private final String contentTypeOverride;


    private ContentTypeRestrictedMessageMapper(final MessageMapper delegate, @Nullable final String contentTypeOverride) {
        this.delegate = checkNotNull(delegate);
        this.contentTypeOverride = contentTypeOverride;
    }

    public static MessageMapper of(final MessageMapper mapper) {
        return new ContentTypeRestrictedMessageMapper(mapper, null);
    }

    public static MessageMapper of(final MessageMapper mapper, final String contentTypeOverride) {
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
        requireMatchingContentType(message);
        return delegate.map(message);
    }

    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        return delegate.map(adaptable);
    }

    private void requireConfiguredContentType() {
        if (!getContentType().isPresent()) {
            throw new IllegalArgumentException("Required configuration property missing: '%s'" +
                    MessageMapperConfigurationProperties.CONTENT_TYPE);
        }
    }

    private void requireMatchingContentType(final ExternalMessage internalMessage) {

        final String contentType = getContentType().filter(s -> !s.isEmpty()).orElseThrow(
                () -> new IllegalArgumentException(String.format(
                        "A matching content type is required, but none configured. Set a content type with the following key in configuration: %s",
                        MessageMapperConfigurationProperties.CONTENT_TYPE))
        );

        final String actualContentType = findContentType(internalMessage)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Message headers do not contain a value for %s", CONTENT_TYPE_KEY)));

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

    /**
     * Identifies and gets a configured content type of a protocol adaptable.
     *
     * @param adaptable the message
     * @return the content type if found
     */
    private static Optional<String> findContentType(final Adaptable adaptable) {
        checkNotNull(adaptable);
        return adaptable.getHeaders().map(h -> h.entrySet().stream()
                .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null));
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
