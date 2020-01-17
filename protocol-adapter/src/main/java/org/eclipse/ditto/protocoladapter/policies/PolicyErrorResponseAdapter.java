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
package org.eclipse.ditto.protocoladapter.policies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.protocoladapter.AbstractErrorResponseAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;

/**
 * Adapter for mapping a {@link PolicyErrorResponse} to and from an {@link Adaptable}.
 */
final class PolicyErrorResponseAdapter extends AbstractErrorResponseAdapter<PolicyErrorResponse> {

    private PolicyErrorResponseAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        super(headerTranslator, errorRegistry);
    }

    /**
     * Returns a new PolicyErrorResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @param errorRegistry TODO
     * @return the adapter.
     */
    public static PolicyErrorResponseAdapter of(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        return new PolicyErrorResponseAdapter(requireNonNull(headerTranslator), errorRegistry);
    }

    @Override
    public TopicPathBuilder getTopicPathBuilder(final PolicyErrorResponse errorResponse) {
        return ProtocolFactory.newTopicPathBuilder(errorResponse.getEntityId());
    }

    @Override
    public PolicyErrorResponse buildErrorResponse(final TopicPath topicPath, final DittoRuntimeException exception,
            final DittoHeaders dittoHeaders) {
        return PolicyErrorResponse.of(PolicyId.of(topicPath.getNamespace(), topicPath.getId()), exception,
                dittoHeaders);
    }
}
