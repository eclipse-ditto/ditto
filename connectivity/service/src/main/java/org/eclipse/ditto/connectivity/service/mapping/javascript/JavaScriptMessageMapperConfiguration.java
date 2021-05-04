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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import static org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperConfigurationProperties.LOAD_BYTEBUFFER_JS;
import static org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperConfigurationProperties.LOAD_LONG_JS;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;

/**
 * Configuration properties for JavaScript MassageMapper.
 */
public interface JavaScriptMessageMapperConfiguration extends MessageMapperConfiguration {

    /**
     * @return the mappingScript responsible for mapping incoming messages.
     */
    default Optional<String> getIncomingScript() {
        return findProperty(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT);
    }

    /**
     * @return the mappingScript responsible for mapping outgoing messages.
     */
    default Optional<String> getOutgoingScript() {
        return findProperty(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT);
    }

    /**
     * @return whether to load "bytebuffer.js" library.
     */
    default boolean isLoadBytebufferJS() {
        return findProperty(LOAD_BYTEBUFFER_JS, JsonValue::isBoolean, JsonValue::asBoolean)
                .orElseGet(() -> findProperty(LOAD_BYTEBUFFER_JS).map(Boolean::parseBoolean).orElse(false));
    }

    /**
     * @return whether to load "long.js" library.
     */
    default boolean isLoadLongJS() {
        return findProperty(LOAD_LONG_JS, JsonValue::isBoolean, JsonValue::asBoolean)
                .orElseGet(() -> findProperty(LOAD_BYTEBUFFER_JS).map(Boolean::parseBoolean).orElse(false));
    }

    /**
     * Specific builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    interface Builder extends MessageMapperConfiguration.Builder<JavaScriptMessageMapperConfiguration> {

        /**
         * Configures the mappingScript responsible for mapping incoming messages.
         *
         * @param mappingScript the incoming mapping script.
         * @return this builder for chaining.
         */
        default Builder incomingScript(@Nullable final String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT,
                        JsonValue.of(mappingScript));
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT);
            }
            return this;
        }

        /**
         * Configures the mappingScript responsible for mapping outgoing messages.
         *
         * @param mappingScript the outgoing mapping script.
         * @return this builder for chaining.
         */
        default Builder outgoingScript(@Nullable final String mappingScript) {
            if (mappingScript != null) {
                getProperties().put(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT,
                        JsonValue.of(mappingScript));
            } else {
                getProperties().remove(JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT);
            }
            return this;
        }

        /**
         * Configures whether to load "bytebuffer.js" library.
         *
         * @param load whether to load "bytebuffer.js" library.
         * @return this builder for chaining.
         */
        default Builder loadBytebufferJS(final boolean load) {
            getProperties().put(LOAD_BYTEBUFFER_JS, JsonValue.of(load));
            return this;
        }

        /**
         * Configures whether to load "long.js" library.
         *
         * @param load whether to load "long.js" library.
         * @return this builder for chaining.
         */
        default Builder loadLongJS(final boolean load) {
            getProperties().put(LOAD_LONG_JS, JsonValue.of(load));
            return this;
        }

    }

}
