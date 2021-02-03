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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.MergeCommandResponseAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing} to and from an
 * {@link org.eclipse.ditto.protocoladapter.Adaptable}.
 */
final class ThingMergeCommandResponseAdapter extends AbstractThingAdapter<MergeThingResponse>
        implements MergeCommandResponseAdapter {

    private final SignalMapper<MergeThingResponse>
            signalMapper = SignalMapperFactory.newThingMergeResponseSignalMapper();

    private ThingMergeCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingMergeCommandResponseMappingStrategies(), headerTranslator,
                ThingMergePathMatcher.getInstance());
    }

    /**
     * Returns a new ThingModifyCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingMergeCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingMergeCommandResponseAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return payloadPathMatcher.match(path);
    }

    @Override
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return RESPONSES_CRITERION;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final MergeThingResponse signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }
}
