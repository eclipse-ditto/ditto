/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.util.Map;
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
        final Map<String, String> properties = getProperties();
        @Nullable final String v = properties.get(JavaScriptMessageMapperConfigurationProperties.LOAD_BYTEBUFFER_JS);
        if (null != v) {
            return Boolean.valueOf(v);
        }
        return false;
    }

    /**
     * @return whether to load "long.js" library.
     */
    default boolean isLoadLongJS() {
        final Map<String, String> properties = getProperties();
        @Nullable final String v = properties.get(JavaScriptMessageMapperConfigurationProperties.LOAD_LONG_JS);
        if (null != v) {
            return Boolean.valueOf(v);
        }
        return false;
    }

    /**
     * Specific builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    interface Builder extends MessageMapperConfiguration.Builder<Builder, JavaScriptMessageMapperConfiguration> {

        /**
         * Configures the mappingScript responsible for mapping incoming messages.
         *
         * @param mappingScript the incoming mapping script.
         * @return this builder for chaining.
         */
        default Builder incomingScript(@Nullable final String mappingScript) {
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
         * @param mappingScript the outgoing mapping script.
         * @return this builder for chaining.
         */
        default Builder outgoingScript(@Nullable final String mappingScript) {
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
         * @param load whether to load "bytebuffer.js" library.
         * @return this builder for chaining.
         */
        default Builder loadBytebufferJS(final boolean load) {
            getProperties().put(JavaScriptMessageMapperConfigurationProperties.LOAD_BYTEBUFFER_JS,
                    Boolean.toString(load));
            return this;
        }

        /**
         * Configures whether to load "long.js" library.
         *
         * @param load whether to load "long.js" library.
         * @return this builder for chaining.
         */
        default Builder loadLongJS(final boolean load) {
            getProperties().put(JavaScriptMessageMapperConfigurationProperties.LOAD_LONG_JS,
                    Boolean.toString(load));
            return this;
        }

    }

}
