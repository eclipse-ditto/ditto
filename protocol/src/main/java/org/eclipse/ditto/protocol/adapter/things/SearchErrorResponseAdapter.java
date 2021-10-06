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

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractErrorResponseAdapter;
import org.eclipse.ditto.thingsearch.model.signals.commands.SearchErrorResponse;

/**
 * Adapter for mapping a {@link SearchErrorResponse} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
 *
 * @since 2.2.0
 */
final class SearchErrorResponseAdapter extends AbstractErrorResponseAdapter<SearchErrorResponse>
        implements ThingAdapter<SearchErrorResponse> {

    private SearchErrorResponseAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        super(headerTranslator, errorRegistry);
    }

    /**
     * Returns a new SearchErrorResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @param errorRegistry the {@link org.eclipse.ditto.base.model.signals.ErrorRegistry} used for the mapping
     * @return the adapter.
     */
    public static SearchErrorResponseAdapter of(final HeaderTranslator headerTranslator,
            final ErrorRegistry<DittoRuntimeException> errorRegistry) {
        return new SearchErrorResponseAdapter(requireNonNull(headerTranslator), errorRegistry);
    }

    @Override
    public TopicPath getTopicPath(final SearchErrorResponse errorResponse,
            final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilderFromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .none()
                .search()
                .error()
                .build();
    }

    @Override
    public boolean supportsWildcardTopics() {
        return true;
    }

    @Override
    public Set<TopicPath.SearchAction> getSearchActions() {
        return EnumSet.of(TopicPath.SearchAction.ERROR);
    }

    @Override
    public SearchErrorResponse buildErrorResponse(final TopicPath topicPath, final DittoRuntimeException exception,
            final DittoHeaders dittoHeaders) {
        return SearchErrorResponse.of(exception, dittoHeaders);
    }
}
