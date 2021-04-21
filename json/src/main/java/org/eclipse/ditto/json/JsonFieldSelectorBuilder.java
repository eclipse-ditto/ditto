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

import javax.annotation.Nullable;

/**
 * A mutable builder with a fluent API for a {@link JsonFieldSelector}. Implementations of this interface are normally
 * not thread safe and not reusable.
 */
public interface JsonFieldSelectorBuilder extends Iterable<JsonPointer> {

    /**
     * Adds the given JSON pointer(s) to the field selector to be built.
     *
     * @param pointer a JSON pointer to be added to the field selector. Adding {@code null} has no effect.
     * @param furtherPointers further JSON pointers to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherPointers} is {@code null}.
     */
    JsonFieldSelectorBuilder addPointer(@Nullable JsonPointer pointer, JsonPointer... furtherPointers);

    /**
     * Adds the given JSON pointers to the field selector to be built.
     *
     * @param pointers the JSON pointers to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code pointers} is {@code null}.
     */
    JsonFieldSelectorBuilder addPointers(Iterable<JsonPointer> pointers);

    /**
     * Adds the given JSON pointer(s) (provided as string) to the field selector to be built.
     *
     * @param pointerString a JSON pointer string representation to be added to the field selector. Adding {@code null}
     * has no effect.
     * @param furtherPointerStrings further JSON pointer string representations to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherPointerStrings} is {@code null}.
     */
    JsonFieldSelectorBuilder addPointerString(@Nullable String pointerString, String... furtherPointerStrings);

    /**
     * Adds the given JSON pointers (provided as string) to the field selector to be built.
     *
     * @param pointerStrings the JSON pointers to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code pointerStrings} is {@code null}.
     */
    JsonFieldSelectorBuilder addPointerStrings(Iterable<String> pointerStrings);

    /**
     * Adds the JSON pointer of each given {@link JsonFieldDefinition} to the field selector to be built.
     *
     * @param fieldDefinition the JSON field definition to be added to the field selector. Adding {@code null} has no
     * effect.
     * @param furtherFieldDefinitions the JSON field definitions to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherFieldDefinitions} is {@code null}.
     */
    JsonFieldSelectorBuilder addFieldDefinition(@Nullable JsonFieldDefinition<?> fieldDefinition,
            JsonFieldDefinition<?>... furtherFieldDefinitions);

    /**
     * Adds the JSON pointer of each given {@link JsonFieldDefinition}s to the field selector to be built.
     *
     * @param fieldDefinitions the JSON field definitions providing JSON pointers to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinitions} is {@code null}.
     */
    JsonFieldSelectorBuilder addFieldDefinitions(Iterable<JsonFieldDefinition<?>> fieldDefinitions);

    /**
     * Adds the Json Pointers from the given {@link JsonFieldSelector}s to the field selector to be built.
     *
     * @param fieldSelector the JSON field selector whose JSON pointers are to be added to the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldSelector} is {@code null}.
     */
    JsonFieldSelectorBuilder addFieldSelector(JsonFieldSelector fieldSelector);

    /**
     * Adds the JSON pointers contained in the new JSON field selector retrieved by parsing the given string. If the
     * JSON field selector string is {@code null} or empty this means that no fields were selected thus this method
     * effectively does nothing.
     * <p>
     * For example, the field selector string
     * </p>
     * <pre>
     * "thingId,attributes(acceleration,someData(foo,bar/baz)),features/key"
     * </pre>
     * would lead to a JSON field selector which consists of the following JSON pointers:
     * <ul>
     *     <li>{@code "thingId"},</li>
     *     <li>{@code "attributes/acceleration"},</li>
     *     <li>{@code "attributes/someData/foo"},</li>
     *     <li>{@code "attributes/someData/bar/baz"},</li>
     *     <li>{@code "features/key"}.</li>
     * </ul>
     *
     * @param fieldSelectorString string to be transformed into a JSON field selector object.
     * @param options the JsonParseOptions to apply when parsing the {@code fieldSelectorString}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code options} is {@code null}.
     * @throws JsonFieldSelectorInvalidException if {@code fieldSelectorString} does not contain a closing parenthesis
     * ({@code )}) for each opening parenthesis ({@code (}).
     * @throws IllegalStateException if {@code fieldSelectorString} cannot be decoded as UTF-8.
     */
    JsonFieldSelectorBuilder addFieldSelectorString(@Nullable String fieldSelectorString, JsonParseOptions options);

    /**
     * Adds the JSON pointers contained in the new JSON field selector retrieved by parsing the given string. If the
     * JSON field selector string is {@code null} or empty this means that no fields were selected thus this method
     * effectively does nothing. <em>This method does no URL-encoding!</em>
     * <p>
     * For example, the field selector string
     * </p>
     * <pre>
     * "thingId,attributes(acceleration,someData(foo,bar/baz)),features/key"
     * </pre>
     * <p>
     * would lead to a JSON field selector which consists of the following JSON pointers:
     * <ul>
     *     <li>{@code "thingId"},</li>
     *     <li>{@code "attributes/acceleration"},</li>
     *     <li>{@code "attributes/someData/foo"},</li>
     *     <li>{@code "attributes/someData/bar/baz"},</li>
     *     <li>{@code "features/key"}.</li>
     * </ul>
     *
     * @param fieldSelectorString string to be transformed into a JSON field selector object.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code options} is {@code null}.
     * @throws JsonFieldSelectorInvalidException if {@code fieldSelectorString} does not contain a closing parenthesis
     * ({@code )}) for each opening parenthesis ({@code (}).
     * @throws IllegalStateException if {@code fieldSelectorString} cannot be decoded as UTF-8.
     * @see #addFieldSelectorString(String, JsonParseOptions)
     */
    JsonFieldSelectorBuilder addFieldSelectorString(@Nullable String fieldSelectorString);

    /**
     * Removes the given JSON Pointer from the field selector to be built.
     *
     * @param jsonPointer the JSON pointer to be removed from the field selector. Removing {@code null} has no effect.
     * @return this builder to allow method chaining.
     */
    JsonFieldSelectorBuilder removePointer(@Nullable JsonPointer jsonPointer);

    /**
     * Removes the given JSON Pointers from the field selector to be built.
     *
     * @param jsonPointers the JSON pointers to be removed from the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code jsonPointers} is {@code null}.
     */
    JsonFieldSelectorBuilder removePointers(Iterable<JsonPointer> jsonPointers);

    /**
     * Removes the JSON Pointer with the given String representation from the field selector to be built.
     *
     * @param pointerString the string representation of the JSON pointer to be removed from the field selector.
     * Removing {@code null} has no effect.
     * @return this builder to allow method chaining.
     */
    JsonFieldSelectorBuilder removePointerString(@Nullable String pointerString);

    /**
     * Removes the JSON Pointers with the given String representations from the field selector to be built.
     *
     * @param pointerStrings the string representations of the JSON pointers to be removed from the field selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code pointerStrings} is {@code null}.
     */
    JsonFieldSelectorBuilder removePointerStrings(Iterable<String> pointerStrings);

    /**
     * Removes the JSON Pointer of the given JSON field definition from the field selector to be built.
     *
     * @param fieldDefinition the field definition which provides the JSON pointer to be removed from the field
     * selector. Removing {@code null} has no effect.
     * @return this builder to allow method chaining.
     */
    JsonFieldSelectorBuilder removeFieldDefinition(@Nullable JsonFieldDefinition<?> fieldDefinition);

    /**
     * Removes the JSON Pointers of the given JSON field definitions from the field selector to be built.
     *
     * @param fieldDefinitions the field definitions which provides the JSON pointers to be removed from the field
     * selector.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinitions} is {@code null}.
     */
    JsonFieldSelectorBuilder removeFieldDefinitions(Iterable<JsonFieldDefinition<?>> fieldDefinitions);

    /**
     * Creates a new {@link JsonFieldSelector} instance containing all JSON Pointers which were added beforehand.
     *
     * @return a new JsonFieldSelector.
     */
    JsonFieldSelector build();

}
