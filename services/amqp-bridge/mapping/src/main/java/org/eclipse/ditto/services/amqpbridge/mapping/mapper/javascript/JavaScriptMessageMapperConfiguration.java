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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;

/**
 * Configuration properties for JavaScript MassageMapper.
 */
public interface JavaScriptMessageMapperConfiguration extends MessageMapperConfiguration {

    /**
     * @return the mappingScript responsible for mapping incoming messages (e.g. via AMQP).
     */
    default Optional<String> getIncomingMappingScript() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.INCOMING_MAPPING_SCRIPT));
    }

    /**
     * @return the mappingScript responsible for mapping outgoing messages (e.g. to AMQP).
     */
    default Optional<String> getOutgoingMappingScript() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.OUTGOING_MAPPING_SCRIPT));
    }

    /**
     * @return whether to load "bytebuffer.js" library.
     */
    default boolean isLoadBytebufferJS() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.LOAD_BYTEBUFFER_JS))
                .map(Boolean::valueOf)
                .orElse(false);
    }

    /**
     * @return whether to load "long.js" library.
     */
    default boolean isLoadLongJS() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.LOAD_LONG_JS))
                .map(Boolean::valueOf)
                .orElse(false);
    }

    /**
     * @return whether to load "mustache.js" library.
     */
    default boolean isLoadMustacheJS() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.LOAD_MUSTACHE_JS))
                .map(Boolean::valueOf)
                .orElse(false);
    }

    /**
     * Specific builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    interface Builder extends MessageMapperConfiguration.Builder<JavaScriptMessageMapperConfiguration> {

        /**
         * Configures the mappingScript responsible for mapping incoming messages (e.g. via AMQP).
         *
         * @param mappingScript the incoming mapping script
         * @return this builder for chaining
         */
        default Builder incomingMappingScript(@Nullable String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.INCOMING_MAPPING_SCRIPT,
                        mappingScript);
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.INCOMING_MAPPING_SCRIPT);
            }
            return this;
        }

        /**
         * Configures the mappingScript responsible for mapping outgoing messages (e.g. to AMQP).
         *
         * @param mappingScript the outgoing mapping script
         * @return this builder for chaining
         */
        default Builder outgoingMappingScript(@Nullable String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.OUTGOING_MAPPING_SCRIPT,
                        mappingScript);
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.OUTGOING_MAPPING_SCRIPT);
            }
            return this;
        }

        /**
         * Configures whether to load "bytebuffer.js" library.
         *
         * @param load whether to load "bytebuffer.js" library
         * @return this builder for chaining
         */
        default Builder loadBytebufferJS(boolean load) {
            getProperties().put(JavaScriptMessageMapperConfigurationProperties.LOAD_BYTEBUFFER_JS,
                    Boolean.toString(load));
            return this;
        }

        /**
         * Configures whether to load "long.js" library.
         *
         * @param load whether to load "long.js" library
         * @return this builder for chaining
         */
        default Builder loadLongJS(boolean load) {
            getProperties().put(JavaScriptMessageMapperConfigurationProperties.LOAD_LONG_JS,
                    Boolean.toString(load));
            return this;
        }

        /**
         * Configures whether to load "mustache.js" library.
         *
         * @param load whether to load "mustache.js" library
         * @return this builder for chaining
         */
        default Builder loadMustacheJS(boolean load) {
            getProperties().put(JavaScriptMessageMapperConfigurationProperties.LOAD_MUSTACHE_JS,
                    Boolean.toString(load));
            return this;
        }

    }
}
