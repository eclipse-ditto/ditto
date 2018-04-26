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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.util.Map;

import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;

/**
 * Factory for creating instances of {@link JavaScriptMessageMapperRhino} and configurations of it.
 */
public final class JavaScriptMessageMapperFactory {

    private JavaScriptMessageMapperFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a MessageMapperConfigurationBuilder for JavaScript.
     *
     * @param properties the Map of configuration properties to initialize the builder with
     * @return the ConfigurationBuilder
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMessageMapperConfigurationBuilder(
            final Map<String, String> properties) {

        return new ImmutableJavaScriptMessageMapperConfiguration.Builder(properties);
    }

    /**
     * Creates a new JavaScript MessageMapper based on the Rhino engine.
     *
     * @return the new JavaScript MessageMapper
     */
    public static MessageMapper createJavaScriptMessageMapperRhino() {
        return new JavaScriptMessageMapperRhino();
    }
}
