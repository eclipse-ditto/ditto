/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructor;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructorFactory;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;

/**
 * Adapter for mapping a {@link PolicyModifyCommandResponse} to and from an {@link Adaptable}.
 */
public class PolicyModifyCommandResponseAdapter extends AbstractThingAdapter<PolicyModifyCommandResponse> {

    private final AdaptableConstructor<PolicyModifyCommandResponse>
            adaptableConstructor =
            AdaptableConstructorFactory.newPolicyModifyResponseAdaptableConstructor();

    private PolicyModifyCommandResponseAdapter(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }


    /**
     * Returns a new PolicyModifyCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyModifyCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyModifyCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<PolicyModifyCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyModifyCommandResponse>> mappingStrategies = new HashMap<>();


//        addTopLevelResponses(mappingStrategies);
//
//        addAclResponses(mappingStrategies);
//
//        addAttributeResponses(mappingStrategies);
//
//        addFeatureResponses(mappingStrategies);

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(pathMatcher.match(path));
        return topicPath.getGroup() + ".responses:" + commandName;
    }

    @Override
    public Adaptable constructAdaptable(final PolicyModifyCommandResponse commandResponse,
            final TopicPath.Channel channel) {
        return adaptableConstructor.construct(commandResponse, channel);
    }
}
