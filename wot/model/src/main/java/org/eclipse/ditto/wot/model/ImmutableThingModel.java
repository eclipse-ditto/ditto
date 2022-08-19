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

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link ThingModel}.
 */
@Immutable
final class ImmutableThingModel extends AbstractThingSkeleton<ThingModel> implements ThingModel {

    ImmutableThingModel(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public Optional<TmOptional> getTmOptional() {
        return getValue(ThingModel.JsonFields.TM_OPTIONAL)
                .map(TmOptional::fromJson);
    }

    @Override
    protected ThingModel createInstance(final JsonObject newWrapped) {
        return new ImmutableThingModel(newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableThingModel;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
