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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperConfiguration;

/**
 * Immutable implementation of {@link JavaScriptMessageMapperConfiguration}.
 */
@Immutable
final class ImmutableJavaScriptMessageMapperConfiguration extends DefaultMessageMapperConfiguration
        implements JavaScriptMessageMapperConfiguration {

    ImmutableJavaScriptMessageMapperConfiguration(final Map<String, String> properties) {
        super(properties);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * Mutable Builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    static final class Builder implements JavaScriptMessageMapperConfiguration.Builder {


        private Map<String, String> properties;

        Builder(final Map<String, String> properties) {
            this.properties = new HashMap<>(properties); // mutable map!
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public JavaScriptMessageMapperConfiguration build() {
            return new ImmutableJavaScriptMessageMapperConfiguration(properties);
        }
    }
}
