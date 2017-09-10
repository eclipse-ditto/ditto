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
