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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingException;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * Converts a {@link DittoRuntimeException} to an appropriate {@link ErrorResponse} where possible.
 */
final class DittoRuntimeExceptionToErrorResponseFunction
        implements BiFunction<DittoRuntimeException, TopicPath, ErrorResponse<?>> {

    private long headersMaxSize;

    private DittoRuntimeExceptionToErrorResponseFunction(final long headersMaxSize) {
        this.headersMaxSize = headersMaxSize;
    }

    static DittoRuntimeExceptionToErrorResponseFunction of(final long headersMaxSize) {
        return new DittoRuntimeExceptionToErrorResponseFunction(headersMaxSize);
    }

    @Override
    public ErrorResponse<?> apply(final DittoRuntimeException exception, @Nullable final TopicPath topicPath) {
        /*
         * Truncate headers to send in an error response.
         * This is necessary because the consumer actor and the publisher actor may not reside in the same connectivity
         * instance due to cluster routing.
         */
        final DittoHeaders truncatedHeaders = exception.getDittoHeaders().truncate(headersMaxSize);
        if (exception instanceof PolicyException) {
            return toPolicyErrorResponse(exception, truncatedHeaders, topicPath);
        } else if (exception instanceof ThingException) {
            return toThingErrorResponse(exception, truncatedHeaders, topicPath);
        } else if (topicPath != null && topicPath.getGroup().equals(TopicPath.Group.POLICIES)) {
            return toPolicyErrorResponse(exception, truncatedHeaders, topicPath);
        } else if (topicPath != null && topicPath.getGroup().equals(TopicPath.Group.THINGS)) {
            return toThingErrorResponse(exception, truncatedHeaders, topicPath);
        } else {
            return toThingErrorResponse(exception, truncatedHeaders, topicPath);
        }
    }

    private ThingErrorResponse toThingErrorResponse(final DittoRuntimeException exception,
            final DittoHeaders truncatedHeaders,
            @Nullable final TopicPath topicPath) {
        final Optional<EntityId> entityId = getEntityId(exception, topicPath);
        return entityId
                .map(ThingId::of)
                .map(thingId -> ThingErrorResponse.of(thingId, exception, truncatedHeaders))
                .orElseGet(() -> ThingErrorResponse.of(exception, truncatedHeaders));
    }

    private PolicyErrorResponse toPolicyErrorResponse(final DittoRuntimeException exception,
            final DittoHeaders truncatedHeaders,
            @Nullable final TopicPath topicPath) {
        final Optional<EntityId> entityId = getEntityId(exception, topicPath);
        return entityId
                .map(PolicyId::of)
                .map(policyId -> PolicyErrorResponse.of(policyId, exception, truncatedHeaders))
                .orElseGet(() -> PolicyErrorResponse.of(exception, truncatedHeaders));
    }

    private static Optional<EntityId> getEntityId(final DittoRuntimeException e,
            @Nullable final TopicPath topicPath) {
        return Optional.ofNullable(topicPath)
                .flatMap(DittoRuntimeExceptionToErrorResponseFunction::getEntityIdFromTopicPath)
                .or(() -> Optional.ofNullable(e.getDittoHeaders().get(DittoHeaderDefinition.ENTITY_ID.getKey()))
                        .map(entityId -> {
                            final int indexOfSeparator = entityId.indexOf(':');
                            final EntityType entityType = EntityType.of(entityId.substring(0, indexOfSeparator));
                            final String id = entityId.substring(indexOfSeparator + 1);
                            return EntityId.of(entityType, id);
                        })
                );
    }

    private static Optional<EntityId> getEntityIdFromTopicPath(final TopicPath topicPath) {
        switch (topicPath.getGroup()) {
            case THINGS:
                return Optional.of(ThingId.of(topicPath.getNamespace(), topicPath.getEntityName()));
            case POLICIES:
                return Optional.of(PolicyId.of(topicPath.getNamespace(), topicPath.getEntityName()));
            default:
                return Optional.empty();
        }
    }
}
