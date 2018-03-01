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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptMessageMapperConfiguration;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptMessageMapperFactory;

/**
 * TODO TJ doc
 */
public final class MessageMappers {

    /**
     *
     */
    public static final String CONTENT_TYPE_KEY = "content-type";

    /**
     *
     */
    public static final String ACCEPT_KEY = "accept";

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");


    private MessageMappers() {
        throw new AssertionError();
    }

    /**
     * Determines the MessageType based on the passed in Ditto Protocol Adaptable.
     *
     * @param adaptable the Adaptable to determine the message type from
     * @return the message type
     * @throws IllegalArgumentException if the Adaptable contained an unknown MessageType
     */
    public static ExternalMessage.MessageType determineMessageType(final Adaptable adaptable) {
        final TopicPath.Criterion criterion = adaptable.getTopicPath().getCriterion();
        switch (criterion) {
            case COMMANDS:
                if (adaptable.getPayload().getStatus().isPresent()) {
                    return ExternalMessage.MessageType.RESPONSE;
                } else {
                    return ExternalMessage.MessageType.COMMAND;
                }
            case EVENTS:
                return ExternalMessage.MessageType.EVENT;
            case MESSAGES:
                return ExternalMessage.MessageType.MESSAGE;
            case ERRORS:
                return ExternalMessage.MessageType.ERRORS;
            default:
                final String errorMessage = MessageFormat.format("Cannot map '{0}' message. Only [{1}, {2}] allowed.",
                        criterion.getName(), TopicPath.Criterion.COMMANDS, TopicPath.Criterion.EVENTS);
                throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Determines the charset from the passed {@code contentType}, falls back to UTF-8 if no specific one was present
     * in contentType.
     *
     * @param contentType the Content-Type to determine the charset from
     * @return the charset
     */
    public static Charset determineCharset(@Nullable final CharSequence contentType) {
        if (contentType != null) {
            final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
            if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
                return Charset.forName(withCharset[1]);
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Creates a mapper configuration from the given properties
     *
     * @param properties the properties
     * @return the configuration
     */
    public static MessageMapperConfiguration configurationOf(final Map<String, String> properties) {
        return DefaultMessageMapperConfiguration.of(properties);
    }

    /**
     * Creates a new
     * {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptMessageMapperConfiguration.Builder}
     *
     * @return the builder
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder() {

        return createJavaScriptMapperConfigurationBuilder(Collections.emptyMap());
    }

    /**
     * Creates a new
     * {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptMessageMapperConfiguration.Builder}
     * with options.
     *
     * @param options
     * @return
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder(
            final Map<String, String> options) {

        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(options);
    }

    /**
     * Factory method for a rhino mapper
     *
     * @return the mapper
     */
    public static MessageMapper createJavaScriptMessageMapper() {
        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
    }
}
