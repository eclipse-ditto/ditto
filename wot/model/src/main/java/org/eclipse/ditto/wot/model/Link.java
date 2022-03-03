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
 * Link is a {@link BaseLink} _not_ being of {@code rel="icon"}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link</a>
 * @see <a href="https://www.iana.org/assignments/link-relations">Link Relation definitions from IANA</a>
 * @since 2.4.0
 */
public interface Link extends BaseLink<Link> {

    static Link fromJson(final JsonObject jsonObject) {
        return new ImmutableLink(jsonObject);
    }

    static Link.Builder newBuilder() {
        return Link.Builder.newBuilder();
    }

    static Link.Builder newBuilder(final JsonObject jsonObject) {
        return Link.Builder.newBuilder(jsonObject);
    }

    @Override
    default Builder toBuilder() {
        return Builder.newBuilder(toJson());
    }

    interface Builder extends BaseLink.Builder<Builder, Link> {

        static Builder newBuilder() {
            return new MutableLinkBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableLinkBuilder(jsonObject.toBuilder());
        }
    }
}
