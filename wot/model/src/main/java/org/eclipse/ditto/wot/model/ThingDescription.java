/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import org.eclipse.ditto.json.JsonObject;

/**
 * The WoT Thing Description (TD) is a central building block in the W3C Web of Things (WoT) and can be considered as
 * the entry point of a Thing.
 * <p>
 * A Thing Description describes the metadata and interfaces of a Thing, enabling interaction with the Thing
 * through its exposed Properties, Actions, and Events.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#introduction-td">WoT TD Thing Description</a>
 * @since 2.4.0
 */
public interface ThingDescription extends ThingSkeleton<ThingDescription> {

    /**
     * Creates a new ThingDescription from the specified JSON object.
     *
     * @param jsonObject the JSON object representing a Thing Description.
     * @return the ThingDescription.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ThingDescription fromJson(final JsonObject jsonObject) {
        return new ImmutableThingDescription(jsonObject);
    }

    /**
     * Creates a new builder for building a ThingDescription.
     *
     * @return the builder.
     */
    static ThingDescription.Builder newBuilder() {
        return ThingDescription.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a ThingDescription, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ThingDescription.Builder newBuilder(final JsonObject jsonObject) {
        return ThingDescription.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a ThingDescription, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default ThingDescription.Builder toBuilder() {
        return Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building a {@link ThingDescription}.
     */
    interface Builder extends ThingSkeletonBuilder<Builder, ThingDescription> {

        /**
         * Creates a new builder for building a ThingDescription.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableThingDescriptionBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a ThingDescription, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableThingDescriptionBuilder(jsonObject.toBuilder());
        }
    }
}
