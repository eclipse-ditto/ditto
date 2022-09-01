/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.placeholders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Placeholder implementation that replaces {@code thing:id}, {@code thing:namespace} and {@code thing:name}.
 * The input value is a String and must be a valid Thing ID.
 */
@Immutable
final class ImmutableThingPlaceholder extends AbstractEntityIdPlaceholder<ThingId> implements ThingPlaceholder {

    /**
     * Singleton instance of the ImmutableThingPlaceholder.
     */
    static final ImmutableThingPlaceholder INSTANCE = new ImmutableThingPlaceholder();

    @Override
    public String getPrefix() {
        return "thing";
    }

    @Override
    public List<String> resolveValues(final EntityId thingId, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(thingId, "Thing ID");
        if (thingId instanceof ThingId thingId1) {
            return doResolve(thingId1, placeholder);
        } else {
            return Collections.emptyList();
        }
    }
}
