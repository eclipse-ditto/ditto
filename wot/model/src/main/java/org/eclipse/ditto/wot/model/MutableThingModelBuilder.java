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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link ThingModel}s.
 */
final class MutableThingModelBuilder
        extends AbstractThingSkeletonBuilder<ThingModel.Builder, ThingModel>
        implements ThingModel.Builder {

    MutableThingModelBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableThingModelBuilder.class);
    }

    @Override
    public ThingModel build() {
        return new ImmutableThingModel(wrappedObjectBuilder.build());
    }

    @Override
    public ThingModel.Builder setTmOptional(@Nullable final TmOptional tmOptional) {
        if (tmOptional != null) {
            putValue(ThingModel.JsonFields.TM_OPTIONAL, tmOptional.toJson());
        } else {
            remove(ThingModel.JsonFields.TM_OPTIONAL);
        }
        return myself;
    }
}
