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
package org.eclipse.ditto.services.models.connectivity;

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
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final Map<String, String> headers) {
        return new UnmodifiableExternalMessageBuilder(headers);
    }

    /**
     * Creates a new ExternalMessageBuilder based on the passed existing {@code externalMessage}.
     *
     * @param externalMessage the ExternalMessage initialize the builder with.
     * @return the builder.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final ExternalMessage externalMessage) {
        return new UnmodifiableExternalMessageBuilder(externalMessage);
    }

}
