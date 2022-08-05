/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.MergeCommandAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;

/**
 * Adapter for mapping a {@link MergeThing} to and from an {@link Adaptable}.
 */
final class ThingMergeCommandAdapter extends AbstractThingAdapter<MergeThing> implements MergeCommandAdapter {

    private ThingMergeCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingMergeCommandMappingStrategies(),
                SignalMapperFactory.newThingMergeSignalMapper(),
                headerTranslator,
                ThingMergePathMatcher.getInstance());
    }

    /**
     * Returns a new ThingModifyCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingMergeCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingMergeCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return payloadPathMatcher.match(path);
    }

}
