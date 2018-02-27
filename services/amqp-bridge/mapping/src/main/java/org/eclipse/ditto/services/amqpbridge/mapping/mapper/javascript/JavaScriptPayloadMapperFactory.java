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

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;

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
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptConfigurationBuilder(
            final Map<String, String> options) {

        return new ImmutableJavaScriptMessageMapperConfiguration.Builder(options);
    }

    /**
     *
     * @return
     */
    public static MessageMapper createRhino() {
        return new RhinoJavaScriptPayloadMapper();
    }
}
