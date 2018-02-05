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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptPayloadMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptPayloadMapperOptions;

/**
 * TODO doc
 */
public class PayloadMappers {

    /**
     *
     * @param contentType
     * @param rawData
     * @param stringData
     * @param headers
     * @return
     */
    public static PayloadMapperMessage createPayloadMapperMessage(final String contentType,
            @Nullable final ByteBuffer rawData,
            @Nullable final String stringData,
            final Map<String, String> headers) {

        return new ImmutablePayloadMapperMessage(contentType, rawData, stringData, headers);
    }

    /**
     *
     * @return
     */
    public static JavaScriptPayloadMapperOptions.Builder createJavaScriptMapperOptionsBuilder() {

        return createJavaScriptMapperOptionsBuilder(Collections.emptyMap());
    }

    /**
     *
     * @param options
     * @return
     */
    public static JavaScriptPayloadMapperOptions.Builder createJavaScriptMapperOptionsBuilder(
            final Map<String, String> options) {

        return JavaScriptPayloadMapperFactory.createJavaScriptOptionsBuilder(options);
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapperOptions.Builder createMapperOptionsBuilder(final Map<String, String> options) {

        return new ImmutablePayloadMapperOptions.Builder(options);
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapper createJavaScriptRhinoMapper(final PayloadMapperOptions options) {

        return JavaScriptPayloadMapperFactory.createRhino(options);
    }
}
