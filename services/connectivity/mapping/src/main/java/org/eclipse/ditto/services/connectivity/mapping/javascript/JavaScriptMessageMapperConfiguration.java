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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.time.Duration;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;

/**
 * Configuration properties for JavaScript MassageMapper.
 */
public interface JavaScriptMessageMapperConfiguration extends MessageMapperConfiguration {

    /**
     * @return the mappingScript responsible for mapping incoming messages.
     */
    default Optional<String> getIncomingScript() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT));
    }

    /**
     * @return the mappingScript responsible for mapping outgoing messages.
     */
    default Optional<String> getOutgoingScript() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT));
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
     * Returns the maximum script size in bytes of a mapping script to run. Prevents loading big JS dependencies
     * into the script (e.g. jQuery which has ~250kB).
     *
     * @return the configured maximum script size in bytes for a script to run.
     */
    default int getMaxScriptSizeBytes() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_SIZE_BYTES))
                .map(Integer::parseInt)
                .orElse(50 * 1000); // default: 50 kB max.
    }

    /**
     * Returns the maximum execution time of a mapping script to run. Prevents endless loops and too complex scripts.
     *
     * @return the configured maximum execution time for a script to run.
     */
    default Duration getMaxScriptExecutionTime() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_EXECUTION_TIME))
                .map(Duration::parse)
                .orElse(Duration.ofMillis(500)); // default: 500ms max.
    }

    /**
     * Returns the maximum call stack depth in the mapping script. Prevents recursions or other too complex computation.
     *
     * @return the configured maximum call stack depth for a script to run.
     */
    default int getMaxScriptStackDepth() {
        return Optional.ofNullable(
                getProperties().get(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_STACK_DEPTH))
                .map(Integer::parseInt)
                .orElse(10); // default: 10
    }

    /**
     * Specific builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    interface Builder extends MessageMapperConfiguration.Builder<Builder, JavaScriptMessageMapperConfiguration> {

        /**
         * Configures the mappingScript responsible for mapping incoming messages.
         *
         * @param mappingScript the incoming mapping script
         * @return this builder for chaining
         */
        default Builder incomingScript(@Nullable String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT,
                        mappingScript);
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT);
            }
            return this;
        }

        /**
         * Configures the mappingScript responsible for mapping outgoing messages.
         *
         * @param mappingScript the outgoing mapping script
         * @return this builder for chaining
         */
        default Builder outgoingScript(@Nullable String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT,
                        mappingScript);
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT);
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
         * Configures the maximum script size in bytes of a mapping script.
         *
         * @param maxScriptSizeInBytes maximum script size in bytes for a script to run
         * @return this builder for chaining
         */
        default Builder maxScriptSizeBytes(@Nullable Integer maxScriptSizeInBytes) {
            if (maxScriptSizeInBytes != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_SIZE_BYTES,
                        maxScriptSizeInBytes.toString());
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_SIZE_BYTES);
            }
            return this;
        }
        /**
         * Configures the maximum execution time of a mapping script to run.
         *
         * @param maxScriptExecutionTime maximum execution time of a mapping script to run
         * @return this builder for chaining
         */
        default Builder maxScriptExecutionTime(@Nullable Duration maxScriptExecutionTime) {
            if (maxScriptExecutionTime != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_EXECUTION_TIME,
                        maxScriptExecutionTime.toString());
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_EXECUTION_TIME);
            }
            return this;
        }

        /**
         * Configures the maximum call stack depth in the mapping script.
         *
         * @param maxScriptStackDepth maximum call stack depth in the mapping script
         * @return this builder for chaining
         */
        default Builder maxScriptStackDepth(@Nullable Integer maxScriptStackDepth) {
            if (maxScriptStackDepth != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_STACK_DEPTH,
                        maxScriptStackDepth.toString());
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.MAX_SCRIPT_STACK_DEPTH);
            }
            return this;
        }

    }
}
