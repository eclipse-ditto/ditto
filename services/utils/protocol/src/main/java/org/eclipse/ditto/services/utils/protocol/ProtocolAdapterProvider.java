/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.protocol;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Interface for loading protocol adapter at runtime.
 */
public abstract class ProtocolAdapterProvider {

    private final ProtocolConfigReader protocolConfigReader;

    /**
     * This constructor is the obligation of all subclasses of {@code ProtocolAdapterProvider}.
     *
     * @param protocolConfigReader the argument.
     */
    public ProtocolAdapterProvider(final ProtocolConfigReader protocolConfigReader) {
        this.protocolConfigReader = requireNonNull(protocolConfigReader);
    }

    /**
     * Retrieve the protocol config reader given by creation of this object.
     *
     * @return the protocol config reader.
     */
    protected ProtocolConfigReader protocolConfigReader() {
        return protocolConfigReader;
    }

    /**
     * Gets a protocol adapter which is appropriate for the client specified by the given {@code userAgent}.
     *
     * @param userAgent the user-agent header provided by the client
     * @return the protocol adapter.
     */
    public abstract ProtocolAdapter getProtocolAdapter(@Nullable final String userAgent);

    /**
     * Gets a header translator to filter incoming HTTP headers for the protocol adapter.
     *
     * @return the header translator.
     */
    public abstract HeaderTranslator getHttpHeaderTranslator();

    /**
     * Header definition for headers ignored by Ditto.
     */
    @AllValuesAreNonnullByDefault
    protected static final class Ignored implements HeaderDefinition {

        private final String key;

        /**
         * Create an ignored header.
         *
         * @param key the ignored header key.
         */
        public Ignored(final String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Class getJavaType() {
            return Object.class;
        }

        @Override
        public Class getSerializationType() {
            return getJavaType();
        }

        @Override
        public boolean shouldReadFromExternalHeaders() {
            return false;
        }

        @Override
        public boolean shouldWriteToExternalHeaders() {
            return false;
        }

        @Override
        public String toString() {
            return getKey();
        }
    }
}
