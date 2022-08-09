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
package org.eclipse.ditto.protocol.adapter.policies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractErrorResponseAdapter;

/**
 * Adapter for mapping a {@link PolicyErrorResponse} to and from an {@link Adaptable}.
 */
final class PolicyErrorResponseAdapter extends AbstractErrorResponseAdapter<PolicyErrorResponse>
        implements PolicyAdapter<PolicyErrorResponse> {

    private PolicyErrorResponseAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        super(headerTranslator, errorRegistry);
    }

    /**
     * Returns a new PolicyErrorResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @param errorRegistry the {@link ErrorRegistry} used for the mapping
     * @return the adapter.
     */
    public static PolicyErrorResponseAdapter of(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        return new PolicyErrorResponseAdapter(requireNonNull(headerTranslator), errorRegistry);
    }

    @Override
    public TopicPath getTopicPath(final PolicyErrorResponse errorResponse,
            final TopicPath.Channel channel) {
        return addChannelToTopicPathBuilder(ProtocolFactory.newTopicPathBuilder(errorResponse.getEntityId()), channel)
                .build();
    }

    @Override
    public PolicyErrorResponse buildErrorResponse(final TopicPath topicPath, final DittoRuntimeException exception,
            final DittoHeaders dittoHeaders) {
        return PolicyErrorResponse.of(PolicyId.of(topicPath.getNamespace(), topicPath.getEntityName()), exception,
                dittoHeaders);
    }
}
