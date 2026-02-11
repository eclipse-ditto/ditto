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
 * Link is a {@link BaseLink} not being of {@code rel="icon"}.
 * <p>
 * Links provide web linking capabilities to connect the Thing Description to related resources.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link</a>
 * @see <a href="https://www.iana.org/assignments/link-relations">Link Relation definitions from IANA</a>
 * @since 2.4.0
 */
public interface Link extends BaseLink<Link> {

    /**
     * Creates a new Link from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the link.
     * @return the Link.
     */
    static Link fromJson(final JsonObject jsonObject) {
        return new ImmutableLink(jsonObject);
    }

    /**
     * Creates a new builder for building a Link.
     *
     * @return the builder.
     */
    static Link.Builder newBuilder() {
        return Link.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a Link, initialized with the values from the specified JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static Link.Builder newBuilder(final JsonObject jsonObject) {
        return Link.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a Link, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default Builder toBuilder() {
        return Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building a {@link Link}.
     */
    interface Builder extends BaseLink.Builder<Builder, Link> {

        /**
         * Creates a new builder for building a Link.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableLinkBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a Link, initialized with the values from the specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableLinkBuilder(jsonObject.toBuilder());
        }
    }
}
