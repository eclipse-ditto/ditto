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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * A builder to create {@link Adaptable} instances.
 */
public interface AdaptableBuilder {

    /**
     * Sets the given {@code payload} to this builder. A previously set payload is replaced.
     *
     * @param payload the payload to set.
     * @return this builder to allow method chaining.
     */
    AdaptableBuilder withPayload(Payload payload);

    /**
     * Sets the given {@code headers} to this builder. Previously set headers are replaced.
     *
     * @param headers the headers to set.
     * @return this builder to allow method chaining.
     */
    AdaptableBuilder withHeaders(DittoHeaders headers);

    /**
     * Creates a new {@code Adaptable} from the previously set values.
     *
     * @return the adaptable.
     */
    Adaptable build();

}
