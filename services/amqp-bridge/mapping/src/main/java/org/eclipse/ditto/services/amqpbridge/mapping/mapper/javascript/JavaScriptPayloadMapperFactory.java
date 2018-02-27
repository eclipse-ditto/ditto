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

import java.util.Map;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;

/**
 * TODO doc
 */
public final class JavaScriptPayloadMapperFactory {

    private JavaScriptPayloadMapperFactory() {
        throw new AssertionError();
    }

    /**
     *
     * @return
     * @param options
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptOptionsBuilder(
            final Map<String, String> options) {

        return new ImmutableJavaScriptMessageMapperMapperOptions.Builder(options);
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapper createRhino(final MessageMapperConfiguration options) {

        return new RhinoJavaScriptPayloadMapper(options);
    }
}
