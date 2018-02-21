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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

import com.google.common.base.Converter;

/**
 * An abstract message converter which converts a {@link InternalMessage} to a {@link Adaptable} and vice versa.
 * Enhances the basic Converter with convenience logic for dynamic configuration and content type based conversion.
 */
public abstract class MessageMapper extends Converter<InternalMessage, Adaptable> {

    public static final String OPT_CONTENT_TYPE_REQUIRED = "contentTypeRequired";
    public static final String OPT_CONTENT_TYPE = "contentType";

    public static final String CONTENT_TYPE_KEY = "Content-Type";

    /**
     * The message content type expected by this mapper.
     * Not final as it might be set via dynamic configuration.
     */
    @SuppressWarnings({"CanBeFinal", "NullableProblems"})
    private String contentType;

    /**
     * Defines if a content type check is performed.
     * Not final as it might be set via dynamic configuration.
     */
    private boolean isContentTypeRequired;

    public final String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type. Use this in your configure() implementation.
     * @param contentType the content type string
     * @throws IllegalArgumentException if contentType is null or empty
     */
    @SuppressWarnings("WeakerAccess")
    protected void setContentType(@Nonnull final String contentType) {
        checkNotEmpty(contentType, "contentType");
        this.contentType = contentType;
    }


    /**
     * @return if this mapper is configured to enforce a content type.
     */
    @SuppressWarnings("WeakerAccess")
    public final boolean isContentTypeRequired() {
        return isContentTypeRequired;
    }

    /**
     * Sets the content type required flag. Use this in your configure() implementation.
     * @param contentTypeRequired if matching content type is required.
     */
    @SuppressWarnings("WeakerAccess")
    protected final void setContentTypeRequired(final boolean contentTypeRequired) {
        isContentTypeRequired = contentTypeRequired;
    }


    @SuppressWarnings("WeakerAccess")
    protected void requireMatchingContentType(final InternalMessage internalMessage) {
        if (isContentTypeRequired()) {
            //noinspection ConstantConditions (will be set in subclasses)
            if (Objects.isNull(contentType) || contentType.isEmpty()) {
                throw new IllegalArgumentException(String.format("A matching content type is required, but none configured. Set a content type with the following key in configuration: %s",
                        CONTENT_TYPE_KEY));
            }
            final String actualContentType = findContentType(internalMessage)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Message headers do not contain a value for %s", CONTENT_TYPE_KEY)));

            if (!contentType.equalsIgnoreCase(actualContentType)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported value for %s: actual='%s', expected='%s'",
                                CONTENT_TYPE_KEY, actualContentType, contentType));
            }

        }
    }

    /**
     * Identifies and gets a configured content type from a message.
     * @param internalMessage the message
     * @return the content type if found
     */
    public static Optional<String> findContentType(final InternalMessage internalMessage) {
        checkNotNull(internalMessage);
        return internalMessage.getHeaders().entrySet().stream()
                .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    /**
     * Identifies and gets a configured content type from a protocol adaptable.
     * @param adaptable the message
     * @return the content type if found
     */
    public static Optional<String> findContentType(final Adaptable adaptable) {
        checkNotNull(adaptable);
        return adaptable.getHeaders().map(h -> h.entrySet().stream()
                .filter(e -> CONTENT_TYPE_KEY.equalsIgnoreCase(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue).orElse(null));
    }

    /**
     * Applies a configuration to the mapper. Has to apply the configuration as a whole, so check validity before
     * changing anything!
     * @param configuration the configuration
     * @throws IllegalArgumentException if the configuration is invalid.
     */
    public final void configure(@Nonnull final MessageMapperConfiguration configuration){
        checkNotNull(configuration);
        doConfigure(configuration);

        final boolean isContentTypeRequiredValue = configuration.findProperty(OPT_CONTENT_TYPE_REQUIRED).map
                (Boolean::valueOf).orElse(true);
        final String contentTypeValue = configuration.findProperty(OPT_CONTENT_TYPE).orElseThrow(() ->
                new IllegalArgumentException(String.format("Missing option <%s>", OPT_CONTENT_TYPE)));

        setContentTypeRequired(isContentTypeRequiredValue);
        setContentType(contentTypeValue);
    }

    protected abstract void doConfigure(@Nonnull final MessageMapperConfiguration configuration);

    protected abstract Adaptable doForwardMap(final InternalMessage internalMessage);

    protected abstract InternalMessage doBackwardMap(final Adaptable adaptable);

    @Override
    protected final Adaptable doForward(final InternalMessage internalMessage) {
        requireMatchingContentType(internalMessage);
        return doForwardMap(internalMessage);
    }

    @Override
    protected final InternalMessage doBackward(final Adaptable adaptable) {
        return doBackwardMap(adaptable);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final MessageMapper mapper = (MessageMapper) o;
        return isContentTypeRequired == mapper.isContentTypeRequired &&
                Objects.equals(contentType, mapper.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, isContentTypeRequired);
    }
}
