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

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.DefaultMessageMapperConfiguration;

/**
 * TODO doc
 */
final class ImmutableJavaScriptMessageMapperMapperOptions extends DefaultMessageMapperConfiguration
        implements JavaScriptMessageMapperConfiguration {

    ImmutableJavaScriptMessageMapperMapperOptions(final Map<String, String> properties) {
        super(properties);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     *
     */
    static final class Builder implements JavaScriptMessageMapperConfiguration.Builder {


        private Map<String, String> properties;

        Builder(final Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public JavaScriptMessageMapperConfiguration build() {
            return new ImmutableJavaScriptMessageMapperMapperOptions(properties);
        }
    }
}
