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

import java.util.Collections;
import java.util.Map;

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

    private MessageMappers() {
        assert (false);
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
