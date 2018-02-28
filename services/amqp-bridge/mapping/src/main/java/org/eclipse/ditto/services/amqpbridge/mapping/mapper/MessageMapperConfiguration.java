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
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.MessageMapperConfigurationInvalidException;

/**
 * Configuration properties for a {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper}.
 */
public interface MessageMapperConfiguration {

    /**
     *
     * @return
     */
    Map<String, String> getProperties();

    /**
     * Searches the configuration for a specific property.
     *
     * @param propertyName the property name
     * @return the property if present
     */
    default Optional<String> findProperty(final String propertyName) {
        return Optional.ofNullable(getProperties().get(propertyName));
    }

    /**
     * Extracts a required property from the configuration and fails with an exception if missing.
     *
     * @param propertyName the property name
     * @return the property value
     * @throws MessageMapperConfigurationInvalidException if no value for the the requested property name is present
     */
    default String getProperty(final String propertyName) {
        return findProperty(propertyName).orElseThrow(() ->
                MessageMapperConfigurationInvalidException.newBuilder(propertyName).build()
        );
    }

    /**
     * Searches the configuration for the content type property.
     *
     * @return the content type value if present
     */
    default Optional<String> findContentType() {
        return findProperty(MessageMapperConfigurationProperties.CONTENT_TYPE);
    }

    /**
     * Extracts the content type and fails if missing.
     *
     * @return the contentType
     * @throws MessageMapperConfigurationInvalidException if content type is missing
     */
    default String getContentType() {
        return getProperty(MessageMapperConfigurationProperties.CONTENT_TYPE);
    }

    /**
     * Builder for {@link MessageMapperConfiguration} instances to be used as a base for a more concrete builder.
     */
    interface Builder<B extends Builder<?, T>,T extends MessageMapperConfiguration> {

        /**
         * @return the configuration properties as mutable map
         */
        Map<String, String> getProperties();

        /**
         * Configures the Content-Type of the MessageMapperConfiguration.
         *
         * @param contentType the Content-Type
         * @return this builder for chaining
         */
        default B contentType(@Nullable String contentType) {
            if (contentType != null) {
                getProperties().put(MessageMapperConfigurationProperties.CONTENT_TYPE, contentType);
            } else {
                getProperties().remove(MessageMapperConfigurationProperties.CONTENT_TYPE);
            }
            return (B) this;
        }

        /**
         * Builds the builder and returns a new instance of {@link MessageMapperConfiguration}
         *
         * @return the built MessageMapperConfiguration
         */
        T build();
    }

}
