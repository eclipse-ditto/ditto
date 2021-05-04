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
package org.eclipse.ditto.protocol;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
