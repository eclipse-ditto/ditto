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

import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for creating instances of {@link ImmutableJsonFieldSelector}.
 */
@NotThreadSafe
final class ImmutableJsonFieldSelectorBuilder implements JsonFieldSelectorBuilder {

    private final Set<JsonPointer> pointers;
    @Nullable private String jsonFieldSelectorString;

    private ImmutableJsonFieldSelectorBuilder() {
        pointers = new LinkedHashSet<>(); // use LinkedHashSet to preserve insertion order
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldSelectorBuilder}.
     *
     * @return the instance.
     */
    public static ImmutableJsonFieldSelectorBuilder newInstance() {
        return new ImmutableJsonFieldSelectorBuilder();
    }

    @Override
    public JsonFieldSelectorBuilder addPointerString(@Nullable final String pointerString,
            final String... furtherPointerStrings) {

        requireNonNull(furtherPointerStrings, "The further JSON pointer strings to be added must not be null!");

        addPointerString(pointerString);
        for (final String furtherPointerString : furtherPointerStrings) {
            addPointerString(furtherPointerString);
        }

        return this;
    }

    private void addPointerString(@Nullable final CharSequence pointerString) {
        if (null != pointerString) {
            pointers.add(JsonFactory.newPointer(pointerString));
        }
    }

    @Override
    public JsonFieldSelectorBuilder addPointerStrings(final Iterable<String> pointerStrings) {
        requireNonNull(pointerStrings, "The JSON pointer strings to be added must not be null!");

        for (final String pointerString : pointerStrings) {
            addPointerString(pointerString);
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder addPointer(@Nullable final JsonPointer pointer,
            final JsonPointer... furtherPointers) {

        requireNonNull(furtherPointers, "The further JSON pointers to be added must not be null!");

        addPointer(pointer);
        for (final JsonPointer furtherPointer : furtherPointers) {
            addPointer(furtherPointer);
        }

        return this;
    }

    private void addPointer(@Nullable final JsonPointer pointer) {
        final boolean pointerAdded = (null != pointer) && pointers.add(pointer);
        if (null != jsonFieldSelectorString && pointerAdded) {
            // The field selector string can only be preserved as long as no additional pointers are added afterwards.
            jsonFieldSelectorString = null;
        }
    }

    @Override
    public JsonFieldSelectorBuilder addPointers(final Iterable<JsonPointer> pointers) {
        requireNonNull(pointers, "The JSON pointers to be added must not be null!");

        for (final JsonPointer pointer : pointers) {
            addPointer(pointer);
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder addFieldDefinition(@Nullable final JsonFieldDefinition<?> fieldDefinition,
            final JsonFieldDefinition<?>... furtherFieldDefinitions) {

        requireNonNull(furtherFieldDefinitions, "The further JSON field definitions to be added must not be null!");

        addFieldDefinition(fieldDefinition);
        for (final JsonFieldDefinition<?> furtherFieldDefinition : furtherFieldDefinitions) {
            addFieldDefinition(furtherFieldDefinition);
        }

        return this;
    }

    private void addFieldDefinition(@Nullable final JsonFieldDefinition<?> fieldDefinition) {
        if (null != fieldDefinition) {
            addPointer(fieldDefinition.getPointer());
        }
    }

    @Override
    public JsonFieldSelectorBuilder addFieldDefinitions(final Iterable<JsonFieldDefinition<?>> fieldDefinitions) {
        requireNonNull(fieldDefinitions, "The JSON field definitions must not be null!");

        for (final JsonFieldDefinition<?> fieldDefinition : fieldDefinitions) {
            addFieldDefinition(fieldDefinition);
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder addFieldSelector(final JsonFieldSelector fieldSelector) {
        return addPointers(requireNonNull(fieldSelector, "The JSON field selector must not be null!"));
    }

    @Override
    public JsonFieldSelectorBuilder addFieldSelectorString(@Nullable final String fieldSelectorString,
            final JsonParseOptions options) {

        requireNonNull(options, "The JSON parse options must not be null!");
        if (null == fieldSelectorString || fieldSelectorString.isEmpty()) {
            return this;
        }

        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(fieldSelectorString, options);
        final boolean arePointersInitiallyEmpty = pointers.isEmpty();
        addPointers(fieldSelector);
        if (arePointersInitiallyEmpty) {
            // We can only set the field selector string if there were no other pointers beforehand.
            jsonFieldSelectorString = fieldSelectorString;
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder addFieldSelectorString(@Nullable final String fieldSelectorString) {
        final JsonParseOptions options = JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();
        return addFieldSelectorString(fieldSelectorString, options);
    }

    @Override
    public JsonFieldSelectorBuilder removePointer(@Nullable final JsonPointer jsonPointer) {
        final boolean pointerRemoved = (null != jsonPointer) && pointers.remove(jsonPointer);
        if (pointerRemoved) {
            resetJsonFieldSelectorString();
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder removePointers(final Iterable<JsonPointer> jsonPointers) {
        requireNonNull(jsonPointers, "The JSON Pointers to be removed must not be null!");

        for (final JsonPointer jsonPointer : jsonPointers) {
            removePointer(jsonPointer);
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder removePointerString(@Nullable final String pointerString) {
        if (null == pointerString) {
            return this;
        }
        return removePointer(JsonFactory.newPointer(pointerString));
    }

    @Override
    public JsonFieldSelectorBuilder removePointerStrings(final Iterable<String> pointerStrings) {
        requireNonNull(pointerStrings, "The JSON Pointer strings to be removed must not be null!");

        for (final String pointerString : pointerStrings) {
            removePointerString(pointerString);
        }

        return this;
    }

    @Override
    public JsonFieldSelectorBuilder removeFieldDefinition(@Nullable final JsonFieldDefinition<?> fieldDefinition) {
        if (null == fieldDefinition) {
            return this;
        }
        return removePointer(fieldDefinition.getPointer());
    }

    @Override
    public JsonFieldSelectorBuilder removeFieldDefinitions(final Iterable<JsonFieldDefinition<?>> fieldDefinitions) {
        requireNonNull(fieldDefinitions, "The JSON field definitions to be removed must not be null!");

        for (final JsonFieldDefinition<?> fieldDefinition : fieldDefinitions) {
            removeFieldDefinition(fieldDefinition);
        }

        return this;
    }

    private void resetJsonFieldSelectorString() {
        if (null != jsonFieldSelectorString) {
            jsonFieldSelectorString = null;
        }
    }

    @Override
    public Iterator<JsonPointer> iterator() {
        return new JsonPointerIterator(pointers.iterator());
    }

    @Override
    public JsonFieldSelector build() {
        return ImmutableJsonFieldSelector.of(pointers, jsonFieldSelectorString);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonFieldSelectorBuilder that = (ImmutableJsonFieldSelectorBuilder) o;
        return Objects.equals(pointers, that.pointers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "pointers=" + pointers + "]";
    }

    private final class JsonPointerIterator implements Iterator<JsonPointer> {

        private final Iterator<JsonPointer> pointerIterator;
        private JsonPointer currentPointer;

        private JsonPointerIterator(final Iterator<JsonPointer> thePointerIterator) {
            pointerIterator = thePointerIterator;
            currentPointer = null;
        }

        @Override
        public boolean hasNext() {
            return pointerIterator.hasNext();
        }

        @Override
        public JsonPointer next() {
            currentPointer = pointerIterator.next();
            return currentPointer;
        }

        @Override
        public void remove() {
            pointerIterator.remove();
            if (null != currentPointer) {
                resetJsonFieldSelectorString();
                currentPointer = null;
            }
        }

    }

}
