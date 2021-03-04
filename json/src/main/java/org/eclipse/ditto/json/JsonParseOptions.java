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
 * Holding options about how to parse a Json type (JsonObject, JsonPointer, ...) from a String.
 */
public interface JsonParseOptions {

    /**
     * Returns a new mutable builder for {@code JsonParseOptions}.
     *
     * @return the new JsonParseOptionsBuilder.
     */
    static JsonParseOptionsBuilder newBuilder() {
        return JsonFactory.newParseOptionsBuilder();
    }

    /**
     * Returns whether a string to be parsed should be URL decoded before parsing or not.
     *
     * @return whether a string to be parsed should be URL decoded before parsing or not.
     */
    boolean isApplyUrlDecoding();

}
