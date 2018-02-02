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

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptPayloadMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript.JavaScriptPayloadMapperOptions;

/**
 * TODO doc
 */
public class PayloadMappers {

    /**
     *
     * @return
     */
    public static JavaScriptPayloadMapperOptions.Builder createJavaScriptOptionsBuilder() {
        return createJavaScriptOptionsBuilder(Collections.emptyMap());
    }

    /**
     *
     * @return
     */
    public static JavaScriptPayloadMapperOptions.Builder createJavaScriptOptionsBuilder(
            final Map<String, String> options) {
        return JavaScriptPayloadMapperFactory.createJavaScriptOptionsBuilder(options);
    }

    public static PayloadMapper createJavaScriptRhino(final JavaScriptPayloadMapperOptions options) {
        return JavaScriptPayloadMapperFactory.createRhino(options);
    }
}
