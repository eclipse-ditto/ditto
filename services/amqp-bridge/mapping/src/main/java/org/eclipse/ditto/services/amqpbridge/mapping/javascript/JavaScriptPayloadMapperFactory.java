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
package org.eclipse.ditto.services.amqpbridge.mapping.javascript;

import org.eclipse.ditto.services.amqpbridge.mapping.PayloadMapper;

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
     */
    public static JavaScriptPayloadMapperOptions.Builder createPayloadMapperOptionsBuilder() {
        return new ImmutableJavaScriptPayloadMapperOptions.Builder();
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapper createNashornMapper(final JavaScriptPayloadMapperOptions options) {
        return new DynamicNashornPayloadMapper(options);
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapper createNashornSandboxMapper(final JavaScriptPayloadMapperOptions options) {
        return new DynamicNashornSandboxPayloadMapper(options);
    }

    /**
     *
     * @param options
     * @return
     */
    public static PayloadMapper createRhinoMapper(final JavaScriptPayloadMapperOptions options) {
        return new DynamicRhinoPayloadMapper(options);
    }
}
