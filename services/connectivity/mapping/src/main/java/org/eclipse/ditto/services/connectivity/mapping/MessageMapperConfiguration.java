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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;

/**
 * Configuration properties for a {@link MessageMapper}.
 */
public interface MessageMapperConfiguration {

    /**
     * The key of the mapper configuration which all mappers share:
     * A comma separated blacklist of content-types which shall not be handled by the mapper which is configured.
     */
    String CONTENT_TYPE_BLACKLIST = "content-type-blacklist";

    /**
     * @return the ID of the mapping
     */
    String getId();

    /**
     * Returns the configuration properties as Map.
     *
     * @return an unmodifiable Map containing the configuration properties.
     */
    Map<String, String> getProperties();

    /**
     * Searches the configuration for a specific property.
     *
     * @param propertyName the property name.
     * @return the property if present.
     */
    default Optional<String> findProperty(final String propertyName) {
        return Optional.ofNullable(getProperties().get(propertyName));
    }

    /**
     * Extracts a required property from the configuration and fails with an exception if missing.
     *
     * @param propertyName the property name.
     * @return the property value.
     * @throws MessageMapperConfigurationInvalidException if no value for the the requested property name is present
     */
    default String getProperty(final String propertyName) {
        return findProperty(propertyName).orElseThrow(() ->
                MessageMapperConfigurationInvalidException.newBuilder(propertyName).build()
        );
    }

    /**
     * Determines the content-type blacklist for this mapper configuration.
     * All content-types defined in the blacklist are not handled by the mapper configured by this configuration.
     *
     * @return the content-type blacklist.
     */
    default Collection<String> getContentTypeBlacklist() {
        return findProperty(CONTENT_TYPE_BLACKLIST)
                .map(blacklist -> blacklist.split(","))
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    /**
     * Builder for {@link MessageMapperConfiguration} instances to be used as a base for a more concrete builder.
     *
     * @param <T> the type of the MessageMapperConfiguration
     */
    interface Builder<T extends MessageMapperConfiguration> {

        /**
         * @return the configuration properties as mutable map.
         */
        Map<String, String> getProperties();

        /**
         * Builds the builder and returns a new instance of {@link MessageMapperConfiguration}
         *
         * @return the built MessageMapperConfiguration.
         */
        T build();

    }

}
