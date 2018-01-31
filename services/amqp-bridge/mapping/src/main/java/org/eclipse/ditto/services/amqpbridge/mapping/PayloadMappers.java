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
package org.eclipse.ditto.services.amqpbridge.mapping;

import org.eclipse.ditto.services.amqpbridge.mapping.javascript.JavaScriptPayloadMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.javascript.JavaScriptPayloadMapperOptions;

/**
 * TODO doc
 */
public class PayloadMappers {

    /**
     *
     * @return
     */
    public static JavaScriptPayloadMapperOptions.Builder createJavaScriptPayloadMapperOptionsBuilder() {
        return JavaScriptPayloadMapperFactory.createPayloadMapperOptionsBuilder();
    }

    public static PayloadMapper createJavaScriptNashornMapper(final JavaScriptPayloadMapperOptions options) {
        return JavaScriptPayloadMapperFactory.createNashornMapper(options);
    }

    public static PayloadMapper createJavaScriptNashornSandboxMapper(final JavaScriptPayloadMapperOptions options) {
        return JavaScriptPayloadMapperFactory.createNashornSandboxMapper(options);
    }

    public static PayloadMapper createJavaScriptRhinoMapper(final JavaScriptPayloadMapperOptions options) {
        return JavaScriptPayloadMapperFactory.createRhinoMapper(options);
    }
}
