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
package org.eclipse.ditto.gateway.service.streaming.actors;

import static org.eclipse.ditto.placeholders.PlaceholderFactory.newExpressionResolver;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newPlaceholderResolver;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.edge.service.placeholders.EntityIdPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.FeaturePlaceholder;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.ThingPlaceholder;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Sessioned Jsonifiable that supports signal enrichment.
 */
@Immutable
final class SessionedSignal implements SessionedJsonifiable {

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final Signal<?> signal;
    private final DittoHeaders sessionHeaders;
    private final StreamingSession session;
    private final StartedSpan startedSpan;

    SessionedSignal(final Signal<?> signal, final DittoHeaders sessionHeaders, final StreamingSession session,
            final StartedSpan startedSpan) {
        this.signal = signal;
        this.sessionHeaders = sessionHeaders;
        this.session = session;
        this.startedSpan = startedSpan;
    }

    @Override
    public Jsonifiable.WithPredicate<JsonObject, JsonField> getJsonifiable() {
        return signal;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return signal.getDittoHeaders();
    }

    @Override
    public CompletionStage<JsonObject> retrieveExtraFields(@Nullable final SignalEnrichmentFacade facade) {
        final ExpressionResolver expressionResolver = newExpressionResolver(
                newPlaceholderResolver(newHeadersPlaceholder(), getDittoHeaders()),
                newPlaceholderResolver(EntityIdPlaceholder.getInstance(),
                        WithEntityId.getEntityIdOfType(EntityId.class, signal).orElse(null)),
                newPlaceholderResolver(ThingPlaceholder.getInstance(),
                        WithEntityId.getEntityIdOfType(EntityId.class, signal).orElse(null)),
                newPlaceholderResolver(FeaturePlaceholder.getInstance(), signal),
                newPlaceholderResolver(TopicPathPlaceholder.getInstance(), PROTOCOL_ADAPTER.toTopicPath(signal)),
                newPlaceholderResolver(ResourcePlaceholder.getInstance(), signal),
                newPlaceholderResolver(TimePlaceholder.getInstance(), new Object()),
                newPlaceholderResolver(RequestPlaceholder.getInstance(), getDittoHeaders().getAuthorizationContext())
        );
        final Optional<ThingFieldSelector> resolvedExtraFields = session.getExtraFields()
                .flatMap(extraFields -> getExtraFields(expressionResolver, extraFields));
        if (resolvedExtraFields.isPresent()) {
            final Optional<ThingId> thingIdOptional = WithEntityId.getEntityIdOfType(ThingId.class, signal);
            if (facade != null && thingIdOptional.isPresent()) {
                return facade.retrievePartialThing(thingIdOptional.get(), resolvedExtraFields.get(), sessionHeaders,
                        signal);
            }
            final CompletableFuture<JsonObject> future = new CompletableFuture<>();
            future.completeExceptionally(SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
            session.getLogger().withCorrelationId(signal)
                    .warning("Completing extraFields retrieval with SignalEnrichmentFailedException, " +
                            "facade: <{}> - thingId: <{}>", facade, thingIdOptional.orElse(null));
            return future;
        } else {
            return CompletableFuture.completedFuture(JsonObject.empty());
        }
    }

    private Optional<ThingFieldSelector> getExtraFields(final ExpressionResolver expressionResolver,
            final ThingFieldSelector thingFieldSelector) {
        final List<JsonPointer> jsonPointers = thingFieldSelector.getPointers().stream()
                .map(JsonPointer::toString)
                .map(expressionResolver::resolve)
                .flatMap(PipelineElement::toStream)
                .map(JsonPointer::of)
                .toList();
        if(jsonPointers.isEmpty()) {
            return Optional.empty();
        }
        final JsonFieldSelector jsonFieldSelector = JsonFactory.newFieldSelector(jsonPointers);
        return Optional.of(ThingFieldSelector.fromJsonFieldSelector(jsonFieldSelector));
    }

    @Override
    public Optional<StreamingSession> getSession() {
        return Optional.of(session);
    }

    @Override
    public void finishSpan() {
        startedSpan.finish();
    }

}
