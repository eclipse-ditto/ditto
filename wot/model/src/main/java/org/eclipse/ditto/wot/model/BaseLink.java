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
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link</a>
 * @see <a href="https://www.iana.org/assignments/link-relations">Link Relation definitions from IANA</a>
 * @param <L> the type of the BaseLink.
 * @since 2.4.0
 */
public interface BaseLink<L extends BaseLink<L>> extends TypedJsonObject<L>, Jsonifiable<JsonObject> {

    String REL_ICON = "icon";

    static BaseLink<?> fromJson(final JsonObject jsonObject) {
        if (jsonObject.getValue(BaseLinkJsonFields.REL)
                .filter(REL_ICON::equals)
                .isPresent()) {
            return IconLink.fromJson(jsonObject);
        } else {
            return Link.fromJson(jsonObject);
        }
    }

    static Link.Builder newLinkBuilder() {
        return Link.newBuilder();
    }

    static IconLink.Builder newIconLinkBuilder() {
        return IconLink.newBuilder();
    }

    IRI getHref();

    Optional<String> getType();

    Optional<String> getRel();

    Optional<IRI> getAnchor();

    Optional<Hreflang> getHreflang();


    interface Builder<B extends Builder<B, L>, L extends BaseLink<L>> extends TypedJsonObjectBuilder<B, L> {

        B setHref(@Nullable IRI href);

        B setType(@Nullable String type);

        B setRel(@Nullable String rel);

        B setAnchor(@Nullable IRI anchor);

        B setHreflang(@Nullable Hreflang hreflang);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BaseLink.
     */
    @Immutable
    final class BaseLinkJsonFields {

        public static final JsonFieldDefinition<String> HREF = JsonFactory.newStringFieldDefinition(
                "href");

        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition(
                "type");

        public static final JsonFieldDefinition<String> REL = JsonFactory.newStringFieldDefinition(
                "rel");

        public static final JsonFieldDefinition<String> ANCHOR = JsonFactory.newStringFieldDefinition(
                "anchor");

        public static final JsonFieldDefinition<String> HREFLANG = JsonFactory.newStringFieldDefinition(
                "hreflang");

        public static final JsonFieldDefinition<JsonArray> HREFLANG_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "hreflang");

        private BaseLinkJsonFields() {
            throw new AssertionError();
        }
    }
}
