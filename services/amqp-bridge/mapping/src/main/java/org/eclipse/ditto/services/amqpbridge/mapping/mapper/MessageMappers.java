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
 *
 */
public final class MessageMappers {

    public static final String CONTENT_TYPE_KEY = "content-type";


    /**
     *
     * @return
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder() {

        return createJavaScriptMapperConfigurationBuilder(Collections.emptyMap());
    }

    /**
     *
     * @param options
     * @return
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder(
            final Map<String, String> options) {

        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(options);
    }


    public static MessageMapper createJavaScriptMessageMapper() {
        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
    }
}
