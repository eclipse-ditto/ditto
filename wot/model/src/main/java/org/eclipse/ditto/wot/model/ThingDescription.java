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
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#introduction-td">WoT TD Thing Description</a>
 * @since 2.4.0
 */
public interface ThingDescription extends ThingSkeleton<ThingDescription> {

    static ThingDescription fromJson(final JsonObject jsonObject) {
        return new ImmutableThingDescription(jsonObject);
    }

    static ThingDescription.Builder newBuilder() {
        return ThingDescription.Builder.newBuilder();
    }

    static ThingDescription.Builder newBuilder(final JsonObject jsonObject) {
        return ThingDescription.Builder.newBuilder(jsonObject);
    }

    @Override
    default ThingDescription.Builder toBuilder() {
        return Builder.newBuilder(toJson());
    }

    interface Builder extends ThingSkeletonBuilder<Builder, ThingDescription> {

        static Builder newBuilder() {
            return new MutableThingDescriptionBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableThingDescriptionBuilder(jsonObject.toBuilder());
        }
    }
}
