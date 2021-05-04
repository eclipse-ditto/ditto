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
package org.eclipse.ditto.base.model.assertions;


import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Specific assertion for {@link org.eclipse.ditto.base.model.json.Jsonifiable}s.
 */
public final class JsonifiableAssert extends AbstractJsonifiableAssert<JsonifiableAssert, Jsonifiable> {

    /**
     * Constructs a new {@code Jsonifiable} object.
     *
     * @param actual the actual value.
     */
    public JsonifiableAssert(final Jsonifiable actual) {
        super(actual, JsonifiableAssert.class);
    }

}
