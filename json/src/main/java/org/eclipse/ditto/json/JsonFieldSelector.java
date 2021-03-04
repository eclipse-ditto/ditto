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

import java.util.Set;

/**
 * A {@code JsonFieldSelector} is basically a set of {@link JsonPointer}s. While a pointer refers to a single value, a
 * field selector points to multiple values at once. It can be used to compose a new JSON object from an existing JSON
 * object; thereby the field selector defines the set of fields of the new JSON object. This comes in handy when
 * applying REST field selectors or even addressing a REST sub resource.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 */
public interface JsonFieldSelector extends Iterable<JsonPointer> {

    /**
     * Returns a new JSON field selector which is based on the given {@link JsonPointer}(s).
     *
     * @param pointerString representation of a JSON pointer of the field selector to be created.
     * @param furtherPointerStrings additional representations of JSON pointers to form the field selector to be created
     * by this method.
     * @return a new JSON field selector.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static JsonFieldSelector newInstance(final CharSequence pointerString,
            final CharSequence... furtherPointerStrings) {

        return JsonFactory.newFieldSelector(pointerString, furtherPointerStrings);
    }

    /**
     * Returns the {@link JsonPointer}s on which this field selector is based.
     *
     * @return an unmodifiable set of JSON pointers this field selector contains.
     */
    Set<JsonPointer> getPointers();

    /**
     * Returns the size of this JSON field selector, i. e. the amount of JSON pointers it contains.
     *
     * @return the size of this JSON field selector.
     */
    int getSize();

    /**
     * Indicates whether this field selector is empty.
     *
     * @return {@code true} if this field selector does not contain any JSON pointers, {@code false} else.
     */
    boolean isEmpty();

    /**
     * This method has two possible outcomes:
     * <ul>
     * <li>If the original field selector string is available it will be simply returned.</li>
     * <li>Otherwise a typical string representation of this object is returned.</li>
     * </ul>
     *
     * @return either the original JSON field selector string or a typical string representation of this object.
     */
    @Override
    String toString();

}
