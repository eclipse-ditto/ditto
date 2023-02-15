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
package org.eclipse.ditto.thingsearch.model;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * This interface represents a property which is subject of a {@link SearchQuery} like for example the identifier of a
 * Thing or the path to a particular property.
 */
@Immutable
public interface SearchProperty {

    /**
     * Returns a new search filter for checking if this property exists.
     *
     * @return the new search filter.
     */
    PropertySearchFilter exists();

    /**
     * Returns a new search filter for checking if the value of this property is equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter eq(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter eq(int value);

    /**
     * Returns a new search filter for checking if the value of this property is equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter eq(long value);

    /**
     * Returns a new search filter for checking if the value of this property is equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter eq(double value);

    /**
     * Returns a new search filter for checking if the value of this property is equal to the given value.
     *
     * @param value the value to compare the value of this property with, may be {@code null}.
     * @return the new search filter.
     */
    PropertySearchFilter eq(@Nullable String value);

    /**
     * Returns a new search filter for checking if the value of this property is not equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ne(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is not equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ne(int value);

    /**
     * Returns a new search filter for checking if the value of this property is not equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ne(long value);

    /**
     * Returns a new search filter for checking if the value of this property is not equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ne(double value);

    /**
     * Returns a new search filter for checking if the value of this property is not equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter ne(@Nullable String value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than or equal to the given
     * value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ge(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than or equal to the given
     * value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ge(int value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than or equal to the given
     * value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ge(long value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than or equal to the given
     * value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter ge(double value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than or equal to the given
     * value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter ge(String value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter gt(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter gt(int value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter gt(long value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter gt(double value);

    /**
     * Returns a new search filter for checking if the value of this property is greater than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter gt(String value);

    /**
     * Returns a new search filter for checking if the value of this property is less than or equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter le(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is less than or equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter le(int value);

    /**
     * Returns a new search filter for checking if the value of this property is less than or equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter le(long value);

    /**
     * Returns a new search filter for checking if the value of this property is less than or equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter le(double value);

    /**
     * Returns a new search filter for checking if the value of this property is less than or equal to the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter le(String value);

    /**
     * Returns a new search filter for checking if the value of this property is less than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter lt(boolean value);

    /**
     * Returns a new search filter for checking if the value of this property is less than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter lt(int value);

    /**
     * Returns a new search filter for checking if the value of this property is less than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter lt(long value);

    /**
     * Returns a new search filter for checking if the value of this property is less than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     */
    PropertySearchFilter lt(double value);

    /**
     * Returns a new search filter for checking if the value of this property is less than the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter lt(String value);

    /**
     * Returns a new search filter for checking if the value of this property is like the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter like(String value);

    /**
     * Returns a new search filter for checking if the value of this property is case insensitive like the given value.
     *
     * @param value the value to compare the value of this property with.
     * @return the new search filter.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    PropertySearchFilter ilike(String value);

    /**
     * Returns a new search filter for checking if the value of this property is in the given value(s).
     *
     * @param value the value to check if the value of this property is in.
     * @param furtherValues additional values to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    PropertySearchFilter in(boolean value, Boolean... furtherValues);

    /**
     * Returns a new search filter for checking if the value of this property is in the given value(s).
     *
     * @param value the value to check if the value of this property is in.
     * @param furtherValues additional values to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    PropertySearchFilter in(int value, Integer... furtherValues);

    /**
     * Returns a new search filter for checking if the value of this property is in the given value(s).
     *
     * @param value the value to check if the value of this property is in.
     * @param furtherValues additional values to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    PropertySearchFilter in(long value, Long... furtherValues);

    /**
     * Returns a new search filter for checking if the value of this property is in the given value(s).
     *
     * @param value the value to check if the value of this property is in.
     * @param furtherValues additional values to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    PropertySearchFilter in(double value, Double... furtherValues);

    /**
     * Returns a new search filter for checking if the value of this property is in the given value(s).
     *
     * @param value the value to check if the value of this property is in.
     * @param furtherValues additional values to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PropertySearchFilter in(String value, String... furtherValues);

    /**
     * Returns a new search filter for checking if the value of this property is in the given values.
     *
     * @param values the value to check if the value of this property is in.
     * @return the new search filter.
     * @throws NullPointerException if {@code values} is {@code null}.
     * @throws IllegalArgumentException if {@code values} is empty.
     */
    PropertySearchFilter in(Collection<JsonValue> values);

    /**
     * Returns the path of this property.
     *
     * @return the path of this property.
     */
    JsonPointer getPath();

}
