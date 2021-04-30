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
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * Converts a {@link DittoRuntimeException} to an appropriate {@link ErrorResponse} where possible.
 */
final class DittoRuntimeExceptionToErrorResponseFunction
        implements BiFunction<DittoRuntimeException, TopicPath, ErrorResponse<?>> {

    private final long headersMaxSize;

    private DittoRuntimeExceptionToErrorResponseFunction(final long headersMaxSize) {
        this.headersMaxSize = headersMaxSize;
    }

    static DittoRuntimeExceptionToErrorResponseFunction of(final long headersMaxSize) {
        return new DittoRuntimeExceptionToErrorResponseFunction(headersMaxSize);
    }

    @Override
    public ErrorResponse<?> apply(final DittoRuntimeException exception, @Nullable final TopicPath topicPath) {
        final ErrorResponse<?> result;
        if (isPolicyException(exception, topicPath)) {
            result = getPolicyErrorResponse(exception, topicPath);
        } else {
            result = getThingErrorResponse(exception, topicPath);
        }
        return result;
    }

    private static boolean isPolicyException(final DittoRuntimeException exception,
            @Nullable final TopicPath topicPath) {

        return isPolicyExceptionByErrorCode(exception.getErrorCode()) || isPolicyExceptionByTopicPath(topicPath);
    }

    private static boolean isPolicyExceptionByErrorCode(final String errorCode) {
        return errorCode.startsWith(PolicyException.ERROR_CODE_PREFIX);
    }

    private static boolean isPolicyExceptionByTopicPath(@Nullable final TopicPath topicPath) {
        return null != topicPath && topicPath.isGroup(TopicPath.Group.POLICIES);
    }

    private PolicyErrorResponse getPolicyErrorResponse(final DittoRuntimeException exception,
            @Nullable final TopicPath topicPath) {

        return getEntityId(exception, topicPath)
                .map(PolicyId::of)
                .map(policyId -> PolicyErrorResponse.of(policyId, exception, truncateHeaders(exception)))
                .orElseGet(() -> PolicyErrorResponse.of(exception, truncateHeaders(exception)));
    }

    private ThingErrorResponse getThingErrorResponse(final DittoRuntimeException exception,
            @Nullable final TopicPath topicPath) {

        return getEntityId(exception, topicPath)
                .map(ThingId::of)
                .map(thingId -> ThingErrorResponse.of(thingId, exception, truncateHeaders(exception)))
                .orElseGet(() -> ThingErrorResponse.of(exception, truncateHeaders(exception)));
    }

    private static Optional<EntityId> getEntityId(final WithDittoHeaders e, @Nullable final TopicPath topicPath) {
        final Optional<EntityId> result;
        if (null != topicPath) {
            result = getEntityIdFromTopicPath(topicPath);
        } else {
            result = getEntityIdFromDittoHeaders(e.getDittoHeaders());
        }
        return result;
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

    private static Optional<EntityId> getEntityIdFromDittoHeaders(final DittoHeaders dittoHeaders) {
        final Optional<EntityId> result;
        @Nullable final var entityId = dittoHeaders.get(DittoHeaderDefinition.ENTITY_ID.getKey());
        if (null != entityId) {
            final var indexOfSeparator = entityId.indexOf(':');
            final var entityType = EntityType.of(entityId.substring(0, indexOfSeparator));
            final var id = entityId.substring(indexOfSeparator + 1);
            result = Optional.of(EntityId.of(entityType, id));
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private DittoHeaders truncateHeaders(final WithDittoHeaders withDittoHeaders) {

        /*
         * Truncate headers to send in an error response.
         * This is necessary because the consumer actor and the publisher actor may not reside in the same connectivity
         * instance due to cluster routing.
         */
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.truncate(headersMaxSize);
    }

}
