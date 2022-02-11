/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * An element going through some pipeline of functions.
 */
public interface PipelineElement extends Iterable<String> {

    /**
     * Compiled Pattern of a string containing any unresolved non-empty JsonArray-String notations inside.
     * All strings matching this pattern are valid JSON arrays. Not all JSON arrays match this pattern.
     * @since 2.4.0
     */
    Pattern JSON_ARRAY_PATTERN = Pattern.compile("(\\[\"(?:\\\\\"|[^\"])*+\"(?:,\"(?:\\\\\"|[^\"])*+\")*+])");

    /**
     * Get the type of this pipeline element.
     *
     * @return the type.
     */
    Type getType();

    /**
     * Advance a resolved value to the next stage. Other elements are unchanged.
     *
     * @param stringProcessor What to do for resolved values.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onResolved(Function<String, PipelineElement> stringProcessor);

    /**
     * Advance an unresolved value to the next stage. Other elements are unchanged.
     *
     * @param nextPipelineElement supplier of the next pipeline element.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onUnresolved(Supplier<PipelineElement> nextPipelineElement);

    /**
     * Advance a deleted value to the next stage. Other elements are unchanged.
     *
     * @param nextPipelineElement supplier of the next pipeline element.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onDeleted(Supplier<PipelineElement> nextPipelineElement);

    /**
     * Evaluate this pipeline element by a visitor.
     *
     * @param visitor the visitor.
     * @param <T> the type of results.
     * @return the evaluation result.
     */
    <T> T accept(PipelineElementVisitor<T> visitor);

    /**
     * Convert a resolved value into another resolved value and leave other elements untouched.
     *
     * @param mapper what to do about the resolved value.
     * @return the mapped resolved value.
     */
    default PipelineElement map(final Function<String, String> mapper) {
        return onResolved(mapper.andThen(PipelineElement::resolved));
    }

    /**
     * Combine 2 pipeline elements such that unresolved elements are replaced by resolved elements, which is in turn
     * replaced by the deletion signifier.
     * <p>
     * Consider all pipeline elements as a simple lattice with PipelineElementDeleted as top and
     * PipelineElementUnresolved as bottom. This function computes a maximal of 2 elements in the lattice.
     * </p>
     *
     * @param other the other element.
     * @return the combined element.
     */
    default PipelineElement orElse(final PipelineElement other) {
        return onDeleted(() -> this)
                .onResolved(s -> this)
                .onUnresolved(() -> other);
    }

    /**
     * Convert this into an optional string, conflating deletion and resolution failure.
     *
     * @return the optional string.
     */
    default Optional<String> toOptional() {
        return accept(PipelineElement.<Optional<String>>newVisitorBuilder()
                .deleted(Optional::empty)
                .unresolved(Optional::empty)
                .resolved(Optional::of)
                .build());
    }

    /**
     * Converts this pipeline element into a stream of strings, expanding elements being a JsonArray via the
     * {@link #expandJsonArraysInString(String)} method.
     * If the PipelineElement contains a String not being in JsonArray format, this simply returns a Stream of one
     * element.
     * Unresolved or deleted elements will result in an empty stream.
     *
     * @return a stream of strings which this pipeline element did resolve.
     * @since 2.4.0
     */
    default Stream<String> toOptionalStream() {
        return toOptional().map(PipelineElement::expandJsonArraysInString)
                .orElse(Stream.empty());
    }

    /**
     * Checks whether the passed {@code elementValue} contains JsonArrays ({@code ["..."]} and expands those JsonArrays
     * to multiple strings returned as resulting stream of this operation.
     * <p>
     * Is able to handle an arbitrary amount of JsonArrays in the passed elementValue.
     *
     * @param elementValue the string value potentially containing JsonArrays as JsonArray-String values.
     * @return a stream of a single subject when the passed in {@code elementValue} did not contain any
     * JsonArray-String notation or else a stream of multiple strings with the JsonArrays being resolved to multiple
     * results of the stream.
     * @since 2.4.0
     */
    static Stream<String> expandJsonArraysInString(final String elementValue) {
        final Matcher jsonArrayMatcher = JSON_ARRAY_PATTERN.matcher(elementValue);
        final int group = 1;
        if (jsonArrayMatcher.find()) {
            final String beforeMatched = elementValue.substring(0, jsonArrayMatcher.start(group));
            final String matchedStr =
                    elementValue.substring(jsonArrayMatcher.start(group), jsonArrayMatcher.end(group));
            final String afterMatched = elementValue.substring(jsonArrayMatcher.end(group));
            return JsonArray.of(matchedStr).stream()
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .flatMap(arrayStringElem -> expandJsonArraysInString(beforeMatched) // recurse!
                            .flatMap(before -> expandJsonArraysInString(afterMatched) // recurse!
                                    .map(after -> before.concat(arrayStringElem).concat(after))
                            )
                    );
        }
        return Stream.of(elementValue);
    }

    /**
     * Create a builder of a visitor to evaluate pipeline elements.
     *
     * @param <T> the type of results.
     * @return the visitor builder.
     */
    static <T> PipelineElementVisitor.Builder<T> newVisitorBuilder() {
        return ImmutablePipelineElementVisitor.newBuilder();
    }

    /**
     * Creat a pipeline element containing a resolved value.
     *
     * @param value the resolved value.
     * @return the pipeline element.
     */
    static PipelineElement resolved(final String value) {
        return PipelineElementResolved.of(value);
    }

    /**
     * Get the unique pipeline element signifying deletion of the whole string containing the pipeline.
     *
     * @return the deleted element.
     */
    static PipelineElement deleted() {
        return PipelineElementDeleted.INSTANCE;
    }

    /**
     * Get the unique pipeline element signifying failed resolution.
     *
     * @return the unresolved element.
     */
    static PipelineElement unresolved() {
        return PipelineElementUnresolved.INSTANCE;
    }

    /**
     * Types of pipeline element.
     */
    enum Type {

        /**
         * Type of the signifier for deletion.
         */
        DELETED,

        /**
         * Type of resolved values.
         */
        RESOLVED,

        /**
         * Type of the signifier for resolution failure.
         */
        UNRESOLVED
    }
}
