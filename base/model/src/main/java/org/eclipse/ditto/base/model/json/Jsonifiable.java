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
package org.eclipse.ditto.base.model.json;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * A {@code Jsonifiable} is an entity which can be represented as JSON (string).
 *
 * @param <J> the type of the JSON result.
 */
public interface Jsonifiable<J extends JsonValue> {

    /**
     * Returns the latest JsonSchemaVersion the implementing class supports.
     *
     * @return the latest JsonSchemaVersion the implementing class supports.
     */
    default JsonSchemaVersion getLatestSchemaVersion() {
        return Arrays.stream(getSupportedSchemaVersions())
                .max(Comparator.naturalOrder()) // find highest (latest) schema version
                .orElseThrow(() -> // if array is empty, throw:
                        new IllegalStateException(
                                "Jsonifiable does not provide information about which schema versions it implements"));
    }

    /**
     * Returns all supported JsonSchemaVersions the implementing class supports. Must be overwritten for
     * classes/interfaces which do not support the "full range" of JsonSchemaVersions currently available.
     *
     * @return all supported JsonSchemaVersions the implementing class supports.
     */
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the JsonSchemaVersion <b>the instance</b> of this Jsonifiable implements. Must be overridden if schema
     * version is persisted in implementing entity itself! Otherwise falls back to the {@link
     * #getLatestSchemaVersion()}.
     *
     * @return the current JsonSchemaVersion.
     */
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getLatestSchemaVersion();
    }

    /**
     * Returns whether or not this {@code Jsonifiable} implements the given {@code version}.
     *
     * @param version the version.
     * @return whether or not this Jsonifiable implements the given version.
     */
    default boolean implementsSchemaVersion(final int version) {
        return JsonSchemaVersion.forInt(version)
                .map(this::implementsSchemaVersion)
                .orElse(false);
    }

    /**
     * Returns whether or not this {@code Jsonifiable} implements the given {@code schemaVersion}.
     *
     * @param schemaVersion the schema version.
     * @return whether or not this Jsonifiable implements the given schemaVersion.
     */
    default boolean implementsSchemaVersion(final JsonSchemaVersion schemaVersion) {
        return Arrays.asList(getSupportedSchemaVersions()).contains(schemaVersion);
    }

    /**
     * Returns this object as {@link JsonValue}.
     *
     * @return a JSON value representation of this object.
     */
    J toJson();

    /**
     * Returns a JSON string representation of this object.
     *
     * @return a JSON string representation of this object.
     * @see #toJson()
     */
    default String toJsonString() {
        return toJson().toString();
    }

    /**
     * Represents a {@code Jsonifiable} where additionally a {@code Predicate} can be specified which determines the
     * content of the result JSON. The predicate is parametrized as only the implementing class knows how the JSON is
     * being created. In the following example an object is jsonified but only those fields should be included which are
     * part of a particular schema version. First the declaration of the object's class:
     *
     * <pre>
     *    public final class MyObject implements Jsonifiable.WithPredicate&lt;JsonField&gt; {
     *       ...
     *    }
     * </pre>
     * <p>
     * Then the method invocation for creating a JSON object of a particular JSON schema version.
     *
     * <pre>
     *    final MyObject myObject = ...;
     *    final JsonValue myObjectJson = myObject.toJson(JsonSchemaVersion.V_1);
     * </pre>
     * <p>
     * This works because {@code JsonSchemaVersion} implements {@code Predicate<JsonField>}.
     *
     * @param <J> the type of the JSON result.
     * @param <T> the type which the predicate consumes for evaluation.
     */
    interface WithPredicate<J extends JsonValue, T> extends Jsonifiable<J> {

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given predicate.
         *
         * @param predicate determines the content of the result.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if {@code predicate} is {@code null}.
         */
        default J toJson(final Predicate<T> predicate) {
            if (predicate instanceof JsonSchemaVersion) {
                // by default: return REGULAR fields which are not HIDDEN:
                return toJson((JsonSchemaVersion) predicate, (Predicate<T>) FieldType.notHidden());
            } else {
                // Default JsonSchemaVersion for all implementations:
                return toJson(getLatestSchemaVersion(), predicate);
            }
        }

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given predicate.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param predicate determines the content of the result.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if {@code predicate} is {@code null}.
         */
        J toJson(JsonSchemaVersion schemaVersion, Predicate<T> predicate);

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * predicate.
         *
         * @param predicate determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code predicate} is {@code null}.
         * @see #toJson(java.util.function.Predicate)
         */
        default String toJsonString(final Predicate<T> predicate) {
            return toJson(predicate).toString();
        }

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * predicate.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param predicate determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code predicate} is {@code null}.
         * @see #toJson(org.eclipse.ditto.base.model.json.JsonSchemaVersion, java.util.function.Predicate)
         */
        default String toJsonString(final JsonSchemaVersion schemaVersion, final Predicate<T> predicate) {
            return toJson(schemaVersion, predicate).toString();
        }
    }

    /**
     * Represents a {@code Jsonifiable} where additionally a {@code JsonPointer} can be specified to determine the
     * content of the result JSON.
     *
     * @param <J> the type of the JSON result.
     */
    interface WithPointer<J extends JsonValue> extends Jsonifiable<J> {

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given pointer.
         *
         * @param pointer determines the field to be included in the JSON representation.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if {@code pointer} is {@code null}.
         */
        default J toJson(final JsonPointer pointer) {
            return toJson(getLatestSchemaVersion(), pointer);
        }

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given pointer.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param pointer determines the field to be included in the JSON representation.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if {@code pointer} is {@code null}.
         */
        J toJson(JsonSchemaVersion schemaVersion, JsonPointer pointer);

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * pointer.
         *
         * @param pointer determines the field to be included in the JSON representation.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code pointer} is {@code null}.
         * @see #toJson(JsonPointer)
         */
        default String toJsonString(final JsonPointer pointer) {
            return toJson(pointer).toString();
        }

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * pointer.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param pointer determines the field to be included in the JSON representation.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code pointer} is {@code null}.
         * @see #toJson(org.eclipse.ditto.base.model.json.JsonSchemaVersion, JsonPointer)
         */
        default String toJsonString(final JsonSchemaVersion schemaVersion, final JsonPointer pointer) {
            return toJson(schemaVersion, pointer).toString();
        }
    }

    /**
     * Represents a {@code Jsonifiable} where additionally a {@code JsonFieldSelector} can be specified to determine the
     * content of the result JSON.
     */
    interface WithFieldSelector extends Jsonifiable<JsonObject>, WithPointer<JsonObject> {

        /**
         * Returns this object as {@link JsonObject}. The content of the result is determined by the given field
         * selector.
         *
         * @param fieldSelector determines the content of the result.
         * @return a JSON object representation of this object.
         * @throws NullPointerException if {@code fieldSelector} is {@code null}.
         */
        default JsonObject toJson(final JsonFieldSelector fieldSelector) {
            return toJson(getLatestSchemaVersion(), fieldSelector);
        }

        /**
         * Returns this object as {@link JsonObject}. The content of the result is determined by the given field
         * selector.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param fieldSelector determines the content of the result.
         * @return a JSON object representation of this object.
         * @throws NullPointerException if {@code fieldSelector} is {@code null}.
         */
        JsonObject toJson(JsonSchemaVersion schemaVersion, JsonFieldSelector fieldSelector);

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * field selector.
         *
         * @param fieldSelector determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code fieldSelector} is {@code null}.
         * @see #toJson(JsonFieldSelector)
         */
        default String toJsonString(final JsonFieldSelector fieldSelector) {
            return toJson(fieldSelector).toString();
        }

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * field selector.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param fieldSelector determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if {@code fieldSelector} is {@code null}.
         * @see #toJson(org.eclipse.ditto.base.model.json.JsonSchemaVersion, JsonFieldSelector)
         */
        default String toJsonString(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
            return toJson(schemaVersion, fieldSelector).toString();
        }

        @Override
        default JsonObject toJson(final JsonPointer pointer) {
            return toJson(pointer.toFieldSelector());
        }

        @Override
        default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonPointer pointer) {
            return toJson(schemaVersion, pointer.toFieldSelector());
        }
    }

    /**
     * Represents a {@code Jsonifiable} where additionally a {@code Predicate} as well as a {@code JsonFieldSelector}
     * can be specified to determine the content of the result JSON.
     *
     * @param <T> the type which the predicate consumes for evaluation.
     */
    interface WithFieldSelectorAndPredicate<T> extends WithPredicate<JsonObject, T>, WithFieldSelector {

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given predicate as
         * well as the given field selector.
         *
         * @param fieldSelector additionally determines the content of the result.
         * @param predicate determines the content of the result.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if any argument is {@code null}.
         * @see #toJson(java.util.function.Predicate)
         * @see #toJson(JsonFieldSelector)
         */
        default JsonObject toJson(final JsonFieldSelector fieldSelector, final Predicate<T> predicate) {
            return toJson(getLatestSchemaVersion(), predicate).get(fieldSelector);
        }

        /**
         * Returns this object as {@link JsonValue}. The content of the result is determined by the given predicate as
         * well as the given field selector.
         *
         * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
         * @param fieldSelector additionally determines the content of the result.
         * @param predicate determines the content of the result.
         * @return a JSON value representation of this object.
         * @throws NullPointerException if any argument is {@code null}.
         * @see #toJson(java.util.function.Predicate)
         * @see #toJson(JsonFieldSelector)
         */
        default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector,
                final Predicate<T> predicate) {
            return toJson(schemaVersion, predicate).get(fieldSelector);
        }

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * predicate as well as the given field selector.
         *
         * @param fieldSelector additionally determines the content of the result.
         * @param predicate determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if any argument is {@code null}.
         * @see #toJson(java.util.function.Predicate)
         * @see #toJson(JsonFieldSelector)
         */
        default String toJsonString(final JsonFieldSelector fieldSelector, final Predicate<T> predicate) {
            return toJsonString(getLatestSchemaVersion(), fieldSelector, predicate);
        }

        /**
         * Returns a JSON string representation of this object. The content of the result is determined by the given
         * predicate as well as the given field selector.
         *
         * @param schemaVersion the schema version in which to return the JSON.
         * @param fieldSelector additionally determines the content of the result.
         * @param predicate determines the content of the result.
         * @return a JSON string representation of this object.
         * @throws NullPointerException if any argument is {@code null}.
         * @see #toJson(java.util.function.Predicate)
         * @see #toJson(JsonFieldSelector)
         */
        default String toJsonString(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector,
                final Predicate<T> predicate) {
            return toJson(schemaVersion, fieldSelector, predicate).toString();
        }
    }

}
