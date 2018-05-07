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
package org.eclipse.ditto.services.connectivity.mapping;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperFactory;

/**
 * Factory for creating known {@link MessageMapper} instances and helpers useful for {@link MessageMapper}
 * implementations.
 */
public final class MessageMappers {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");


    private MessageMappers() {
        throw new AssertionError();
    }

    /**
     * Determines the charset from the passed {@code contentType}, falls back to UTF-8 if no specific one was present
     * in contentType.
     *
     * @param contentType the Content-Type to determine the charset from
     * @return the charset
     */
    public static Charset determineCharset(@Nullable final CharSequence contentType) {
        if (contentType != null) {
            final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
            if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
                return Charset.forName(withCharset[1]);
            }
        }
        return StandardCharsets.UTF_8;
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
     * {@link JavaScriptMessageMapperConfiguration.Builder}
     *
     * @return the builder
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder() {

        return createJavaScriptMapperConfigurationBuilder(Collections.emptyMap());
    }

    /**
     * Creates a new
     * {@link JavaScriptMessageMapperConfiguration.Builder}
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
