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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A BaseLink "can be viewed as a statement of the form link context has a relation type resource at link target".
 * <p>
 * Links provide a way to connect the Thing Description to related resources, following the Web Linking model
 * defined in RFC 8288.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link</a>
 * @see <a href="https://www.iana.org/assignments/link-relations">Link Relation definitions from IANA</a>
 * @param <L> the type of the BaseLink.
 * @since 2.4.0
 */
public interface BaseLink<L extends BaseLink<L>> extends TypedJsonObject<L>, Jsonifiable<JsonObject> {

    /**
     * The relation type for icon links.
     */
    String REL_ICON = "icon";

    /**
     * Creates a BaseLink from the specified JSON object, automatically determining whether it is
     * an {@link IconLink} or a regular {@link Link} based on the {@code rel} field.
     *
     * @param jsonObject the JSON object representing the link.
     * @return the appropriate BaseLink subtype.
     */
    static BaseLink<?> fromJson(final JsonObject jsonObject) {
        if (jsonObject.getValue(BaseLinkJsonFields.REL)
                .filter(REL_ICON::equals)
                .isPresent()) {
            return IconLink.fromJson(jsonObject);
        } else {
            return Link.fromJson(jsonObject);
        }
    }

    /**
     * Creates a new builder for building a {@link Link}.
     *
     * @return the builder.
     */
    static Link.Builder newLinkBuilder() {
        return Link.newBuilder();
    }

    /**
     * Creates a new builder for building an {@link IconLink}.
     *
     * @return the builder.
     */
    static IconLink.Builder newIconLinkBuilder() {
        return IconLink.newBuilder();
    }

    /**
     * Returns the target IRI of the link.
     *
     * @return the target href IRI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link (href)</a>
     */
    IRI getHref();

    /**
     * Returns the optional hint indicating the expected MIME type of the target resource.
     *
     * @return the optional media type hint.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link (type)</a>
     */
    Optional<String> getType();

    /**
     * Returns the optional relation type that identifies the semantics of the link.
     * <p>
     * Common values include "alternate", "describedby", "item", "collection", etc.
     * </p>
     *
     * @return the optional link relation type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link (rel)</a>
     * @see <a href="https://www.iana.org/assignments/link-relations">IANA Link Relations</a>
     */
    Optional<String> getRel();

    /**
     * Returns the optional anchor IRI that overrides the default context IRI for the link relation.
     *
     * @return the optional anchor IRI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link (anchor)</a>
     */
    Optional<IRI> getAnchor();

    /**
     * Returns the optional language tag(s) indicating the language of the target resource.
     *
     * @return the optional language tag(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link (hreflang)</a>
     */
    Optional<Hreflang> getHreflang();


    /**
     * A mutable builder for creating {@link BaseLink} instances.
     *
     * @param <B> the type of the Builder.
     * @param <L> the type of the BaseLink.
     */
    interface Builder<B extends Builder<B, L>, L extends BaseLink<L>> extends TypedJsonObjectBuilder<B, L> {

        /**
         * Sets the target href IRI.
         *
         * @param href the target IRI, or {@code null} to remove.
         * @return this builder.
         */
        B setHref(@Nullable IRI href);

        /**
         * Sets the media type hint.
         *
         * @param type the media type, or {@code null} to remove.
         * @return this builder.
         */
        B setType(@Nullable String type);

        /**
         * Sets the link relation type.
         *
         * @param rel the relation type, or {@code null} to remove.
         * @return this builder.
         */
        B setRel(@Nullable String rel);

        /**
         * Sets the anchor IRI.
         *
         * @param anchor the anchor IRI, or {@code null} to remove.
         * @return this builder.
         */
        B setAnchor(@Nullable IRI anchor);

        /**
         * Sets the language tag(s).
         *
         * @param hreflang the language tag(s), or {@code null} to remove.
         * @return this builder.
         */
        B setHreflang(@Nullable Hreflang hreflang);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BaseLink.
     */
    @Immutable
    final class BaseLinkJsonFields {

        /**
         * JSON field definition for the target href IRI.
         */
        public static final JsonFieldDefinition<String> HREF = JsonFactory.newStringFieldDefinition(
                "href");

        /**
         * JSON field definition for the media type hint.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition(
                "type");

        /**
         * JSON field definition for the link relation type.
         */
        public static final JsonFieldDefinition<String> REL = JsonFactory.newStringFieldDefinition(
                "rel");

        /**
         * JSON field definition for the anchor IRI.
         */
        public static final JsonFieldDefinition<String> ANCHOR = JsonFactory.newStringFieldDefinition(
                "anchor");

        /**
         * JSON field definition for the language tag (single value).
         */
        public static final JsonFieldDefinition<String> HREFLANG = JsonFactory.newStringFieldDefinition(
                "hreflang");

        /**
         * JSON field definition for the language tags (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> HREFLANG_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "hreflang");

        private BaseLinkJsonFields() {
            throw new AssertionError();
        }
    }
}
