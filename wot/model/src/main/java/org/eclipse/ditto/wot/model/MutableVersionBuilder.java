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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link Version}s.
 */
final class MutableVersionBuilder implements Version.Builder {

    private final JsonObjectBuilder wrappedObjectBuilder;

    MutableVersionBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        this.wrappedObjectBuilder = wrappedObjectBuilder;
    }

    @Override
    public Version.Builder setInstance(final String instance) {
        putValue(Version.JsonFields.INSTANCE, instance);
        return this;
    }

    @Override
    public Version.Builder setModel(final String model) {
        putValue(Version.JsonFields.MODEL, model);
        return this;
    }

    @Override
    public Version build() {
        return new ImmutableVersion(wrappedObjectBuilder.build());
    }

    private <J> void putValue(final JsonFieldDefinition<J> definition, @Nullable final J value) {
        final Optional<JsonKey> keyOpt = definition.getPointer().getRoot();
        if (keyOpt.isPresent()) {
            final JsonKey key = keyOpt.get();
            if (null != value) {
                checkNotNull(value, definition.getPointer().toString());
                wrappedObjectBuilder.remove(key);
                wrappedObjectBuilder.set(definition, value);
            } else {
                wrappedObjectBuilder.remove(key);
            }
        }
    }
}
