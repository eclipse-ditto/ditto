/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.MergeCommandAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;

/**
 * Adapter for mapping a {@link MergeThing} to and from an {@link Adaptable}.
 */
final class ThingMergeCommandAdapter extends AbstractThingAdapter<MergeThing> implements MergeCommandAdapter {

    private final SignalMapper<MergeThing> signalMapper = SignalMapperFactory.newThingMergeSignalMapper();

    private ThingMergeCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingMergeCommandMappingStrategies(), headerTranslator,
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

    @Override
    public Adaptable mapSignalToAdaptable(final MergeThing command, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(command, channel);
    }
}
