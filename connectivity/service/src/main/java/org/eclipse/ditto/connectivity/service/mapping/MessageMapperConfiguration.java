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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;

/**
 * Configuration properties for a {@link MessageMapper}.
 */
public interface MessageMapperConfiguration {

    /**
     * The key of the mapper configuration which all mappers share:
     * A comma separated blocklist of content-types which shall not be handled by the mapper which is configured.
     */
    String CONTENT_TYPE_BLOCKLIST = "content-type-blocklist";

    /**
     * @return the ID of the mapping
     */
    String getId();

    /**
     * Returns the configuration properties as Map.
     *
     * @return an unmodifiable Map containing the configuration properties.
     */
    Map<String, JsonValue> getProperties();

    /**
     * Returns the configuration properties as JSON object.
     *
     * @return configuration properties as JSON object.
     */
    default JsonObject getPropertiesAsJson() {
        return getProperties().entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), entry.getValue()))
                .collect(JsonCollectors.fieldsToObject());
    }

    /**
     * Returns the conditions to check before mapping incoming messages.
     *
     * @return an unmodifiable Set containing the conditions.
     * @since 1.3.0
     */
    Map<String, String> getIncomingConditions();

    /**
     * Returns the conditions to check before mapping outgoing messages.
     *
     * @return an unmodifiable Set containing the conditions.
     * @since 1.3.0
     */
    Map<String, String> getOutgoingConditions();

    /**
     * Searches the configuration for a specific property.
     *
     * @param propertyName the property name.
     * @return the property if present.
     */
    default Optional<String> findProperty(final String propertyName) {
        return Optional.ofNullable(getProperties().get(propertyName)).map(JsonValue::formatAsString);
    }

    /**
     * Searches the configuration for a specific property of a specific type.
     *
     * @param propertyName name of the property.
     * @param typePredicate the type checking predicate.
     * @param typeCast the type casting function.
     * @param <T> the expected value type.
     * @return the property value of the correct type if it exists or an empty optional otherwise.
     */
    default <T> Optional<T> findProperty(final String propertyName,
            final Predicate<JsonValue> typePredicate,
            final Function<JsonValue, T> typeCast) {
        return Optional.ofNullable(getProperties().get(propertyName))
                .filter(typePredicate)
                .map(typeCast);
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
     * Determines the content-type blocklist for this mapper configuration.
     * All content-types defined in the blocklist are not handled by the mapper configured by this configuration.
     *
     * @return the content-type blocklist.
     */
    default Collection<String> getContentTypeBlocklist() {
        return findProperty(CONTENT_TYPE_BLOCKLIST)
                .map(blocklist -> blocklist.split(","))
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
        Map<String, JsonValue> getProperties();

        /**
         * @return the incoming conditions as mutable set.
         * @since 1.3.0
         */
        Map<String, String> getIncomingConditions();

        /**
         * @return the outgoing conditions as mutable set.
         * @since 1.3.0
         */
        Map<String, String> getOutgoingConditions();

        /**
         * Builds the builder and returns a new instance of {@link MessageMapperConfiguration}
         *
         * @return the built MessageMapperConfiguration.
         */
        T build();

    }

}
