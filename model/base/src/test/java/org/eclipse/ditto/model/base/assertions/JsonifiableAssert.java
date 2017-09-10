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
package org.eclipse.ditto.model.base.assertions;


import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Specific assertion for {@link Jsonifiable}s.
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
