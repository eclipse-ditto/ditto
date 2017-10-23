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

import javax.annotation.concurrent.Immutable;

/**
 * An immutable JSON literal like {@code null}, {@code true} or {@code false}.
 */
@Immutable
final class ImmutableJsonLiteral extends AbstractMinimalJsonValueWrapper {

    /**
     * The JSON literal for the boolean value {@code true}.
     */
    static final ImmutableJsonLiteral TRUE = ImmutableJsonLiteral.of(com.eclipsesource.json.Json.TRUE);

    /**
     * The JSON literal for the boolean value {@code false}.
     */
    static final ImmutableJsonLiteral FALSE = ImmutableJsonLiteral.of(com.eclipsesource.json.Json.FALSE);

    private ImmutableJsonLiteral(final com.eclipsesource.json.JsonValue toWrap) {
        super(toWrap);
    }

    /**
     * Creates a new {@code ImmutableJsonLiteral} object of a Minimal Json Literal.
     *
     * @param minimalJsonLiteral the Minimal Json Literal.
     * @return a new ImmutableJsonLiteral object.
     */
    public static ImmutableJsonLiteral of(final com.eclipsesource.json.JsonValue minimalJsonLiteral) {
        return new ImmutableJsonLiteral(minimalJsonLiteral);
    }

}
