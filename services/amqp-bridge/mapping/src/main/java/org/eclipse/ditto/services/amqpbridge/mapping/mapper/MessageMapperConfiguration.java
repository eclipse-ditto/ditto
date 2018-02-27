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

/**
 * Configuration properties for a {@link AbstractMessageMapper}.
 */
public interface MessageMapperConfiguration {

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
     * @throws IllegalArgumentException if no value present
     */
    default String getProperty(final String propertyName) {
        return findProperty(propertyName).orElseThrow(() ->
                new IllegalArgumentException(String.format("Required configuration property missing: '%s'", propertyName))
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
     * @throws IllegalArgumentException if content type is missing
     */
    default String getContentType() {
        return getProperty(MessageMapperConfigurationProperties.CONTENT_TYPE);
    }

    /**
     *
     */
    interface Builder<T extends MessageMapperConfiguration> {

        /**
         *
         * @return
         */
        Map<String, String> getProperties();

        /**
         *
         * @return
         */
        T build();
    }

}
