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
package org.eclipse.ditto.connectivity.api;

import java.util.Map;

/**
 * Factory used to create instances of {@link ExternalMessage}s.
 */
public final class ExternalMessageFactory {

    private ExternalMessageFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new ExternalMessageBuilder initialized with the passed {@code headers}.
     *
     * @param headers the headers to initialize the builder with.
     * @return the builder.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final Map<String, String> headers) {
        return UnmodifiableExternalMessage.newBuilder(headers);
    }

    /**
     * Creates a new ExternalMessageBuilder based on the passed existing {@code externalMessage}.
     *
     * @param externalMessage the ExternalMessage initialize the builder with.
     * @return the builder.
     * @throws NullPointerException if {@code externalMessage} is {@code null}.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final ExternalMessage externalMessage) {
        return UnmodifiableExternalMessage.newBuilder(externalMessage);
    }

}
