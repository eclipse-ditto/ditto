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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.messaging.InternalMessage;

import com.google.common.base.Converter;


public abstract class MessageMapper extends Converter<InternalMessage, Adaptable> {

    public static String OPT_CONTENT_TYPE_REQUIRED = "contentTypeRequired";

    public static String CONTENT_TYPE_KEY = "Content-Type";

    private String contentType;
    private boolean isContentTypeRequired;

    protected MessageMapper(final String contentType, final boolean isContentTypeRequired) {
        this.contentType = contentType;
        this.isContentTypeRequired = isContentTypeRequired;
    }

    public final String getContentType() {
        return contentType;
    }

    public final boolean isContentTypeRequired() {
        return isContentTypeRequired;
    }

    protected final void setContentTypeRequired(final boolean contentTypeRequired) {
        isContentTypeRequired = contentTypeRequired;
    }

    protected static Optional<String> findOption(final Map<String, String> configuration, final String option) {
        return Optional.ofNullable(configuration.get(option)).filter(s -> !s.isEmpty());
    }

    protected void requireMatchingContentType(final InternalMessage internalMessage) {
        if (isContentTypeRequired) {
            if (Objects.isNull(contentType) || contentType.isEmpty()) {
                throw new IllegalArgumentException("A matching content type is required, but none configured");
            }
            final String actualContentType = internalMessage.getHeaders().entrySet().stream()
                    .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Message headers do not contain a value for %s", CONTENT_TYPE_KEY)));

            if (!contentType.equalsIgnoreCase(actualContentType)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported value for %s: actual='%s', expected='%s'",
                                CONTENT_TYPE_KEY, actualContentType, contentType));
            }

        }
    }

    public abstract void configure(final Map<String, String> configuration);

}
