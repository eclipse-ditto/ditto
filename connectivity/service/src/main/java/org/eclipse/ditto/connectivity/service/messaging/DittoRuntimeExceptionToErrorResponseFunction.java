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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;

/**
 * Converts a {@link DittoRuntimeException} to an appropriate {@link ErrorResponse} where possible.
 */
final class DittoRuntimeExceptionToErrorResponseFunction
        implements BiFunction<DittoRuntimeException, TopicPath, ErrorResponse<?>> {

    private final DittoHeadersValidator dittoHeadersValidator;

    private DittoRuntimeExceptionToErrorResponseFunction(final DittoHeadersValidator dittoHeadersValidator) {
        this.dittoHeadersValidator = dittoHeadersValidator;
    }

    static DittoRuntimeExceptionToErrorResponseFunction of(final DittoHeadersValidator dittoHeadersValidator) {
        return new DittoRuntimeExceptionToErrorResponseFunction(dittoHeadersValidator);
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
                .flatMap(constructEntityIdSafely(PolicyId::of))
                .map(policyId -> PolicyErrorResponse.of(policyId, exception,
                        dittoHeadersValidator.truncate(exception.getDittoHeaders())))
                .orElseGet(() -> PolicyErrorResponse.of(exception,
                        dittoHeadersValidator.truncate(exception.getDittoHeaders()
                                .toBuilder()
                                .removeHeader(DittoHeaderDefinition.ENTITY_ID.getKey())
                                .build())));
    }

    private ThingErrorResponse getThingErrorResponse(final DittoRuntimeException exception,
            @Nullable final TopicPath topicPath) {

        return getEntityId(exception, topicPath)
                .flatMap(constructEntityIdSafely(ThingId::of))
                .map(thingId -> ThingErrorResponse.of(thingId, exception,
                        dittoHeadersValidator.truncate(exception.getDittoHeaders())))
                .orElseGet(() -> ThingErrorResponse.of(exception,
                        dittoHeadersValidator.truncate(exception.getDittoHeaders()
                                .toBuilder()
                                .removeHeader(DittoHeaderDefinition.ENTITY_ID.getKey())
                                .build())));
    }

    private static <T> Function<EntityId, Optional<T>> constructEntityIdSafely(
            final Function<EntityId, T> constructor) {
        return entityId -> {
            try {
                return Optional.of(constructor.apply(entityId));
            } catch (final DittoRuntimeException e) {
                // entity ID is invalid
                return Optional.empty();
            }
        };
    }

    private static Optional<EntityId> getEntityId(final WithDittoHeaders e, @Nullable final TopicPath topicPath) {
        try {
            final Optional<EntityId> result;
            if (null != topicPath) {
                result = getEntityIdFromTopicPath(topicPath);
            } else {
                result = getEntityIdFromDittoHeaders(e.getDittoHeaders());
            }
            return result;
        } catch (final Exception e2) {
            // entity ID from available information is not valid for the entity type
            return Optional.empty();
        }
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

}
