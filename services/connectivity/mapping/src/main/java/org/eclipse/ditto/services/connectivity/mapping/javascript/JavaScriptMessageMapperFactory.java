/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;

/**
 * Factory for creating instances of {@link JavaScriptMessageMapperRhino} and configurations of it.
 */
@Immutable
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
