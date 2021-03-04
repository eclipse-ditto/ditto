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
package org.eclipse.ditto.json;

/**
 * A mutable builder for a {@link JsonParseOptions}. Implementations of this interface are normally not thread safe and
 * not reusable.
 */
public interface JsonParseOptionsBuilder {

    /**
     * Configures to URL decode the String to parse.
     *
     * @return this builder to allow method chaining.
     */
    JsonParseOptionsBuilder withUrlDecoding();

    /**
     * Configures NOT to URL decode the String to parse.
     *
     * @return this builder to allow method chaining.
     */
    JsonParseOptionsBuilder withoutUrlDecoding();

    /**
     * Creates new {@link JsonParseOptions} containing all values which were added beforehand.
     *
     * @return the new JsonParseOptions.
     */
    JsonParseOptions build();

}
