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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * The Thing Model can be used to mainly provide the data model definitions within Things' {@link Properties},
 * {@link Actions}, and/or {@link Events} and can be potentially used as template for creating
 * {@link ThingDescription} instances
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#introduction-tm">WoT TD Thing Model</a>
 * @since 2.4.0
 */
public interface ThingModel extends ThingSkeleton<ThingModel> {

    static ThingModel fromJson(final JsonObject jsonObject) {
        return new ImmutableThingModel(jsonObject);
    }

    static ThingModel.Builder newBuilder() {
        return ThingModel.Builder.newBuilder();
    }

    static ThingModel.Builder newBuilder(final JsonObject jsonObject) {
        return ThingModel.Builder.newBuilder(jsonObject);
    }

    Optional<TmOptional> getTmOptional();

    @Override
    default ThingModel.Builder toBuilder() {
        return ThingModel.Builder.newBuilder(toJson());
    }

    interface Builder extends ThingSkeletonBuilder<Builder, ThingModel> {

        static Builder newBuilder() {
            return new MutableThingModelBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableThingModelBuilder(jsonObject.toBuilder());
        }

        Builder setTmOptional(@Nullable TmOptional tmOptional);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a ThingModel.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonArray> TM_OPTIONAL = JsonFactory.newJsonArrayFieldDefinition(
                "tm:optional");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
