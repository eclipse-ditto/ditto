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
package org.eclipse.ditto.protocol.adapter.acknowledgements;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementsFactory;

/**
 * Adapter for mapping a {@link Acknowledgements} to and from an {@link Adaptable}.
 *
 * @since 1.1.0
 */
final class AcknowledgementsAdapter implements Adapter<Acknowledgements> {

    private final HeaderTranslator headerTranslator;

    private AcknowledgementsAdapter(final HeaderTranslator headerTranslator) {
        this.headerTranslator = headerTranslator;
    }

    static AcknowledgementsAdapter getInstance(final HeaderTranslator headerTranslator) {
        return new AcknowledgementsAdapter(checkNotNull(headerTranslator, "headerTranslator"));
    }

    @Override
    public Acknowledgements fromAdaptable(final Adaptable adaptable) {
        checkNotNull(adaptable, "adaptable");
        final ThingId thingId = getThingId(adaptable);
        return ThingAcknowledgementsFactory.newAcknowledgements(
                thingId,
                gatherContainedAcknowledgements(adaptable, thingId),
                getHttpStatusOrThrow(adaptable),
                adaptable.getDittoHeaders()
        );
    }

    private static ThingId getThingId(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getEntityName());
    }

    private static List<Acknowledgement> gatherContainedAcknowledgements(final Adaptable adaptable,
            final ThingId thingId) {

        final JsonValue adaptablePayloadValue = adaptable.getPayload()
                .getValue()
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Payload.JsonFields.VALUE.getPointer())
                        .build());
        return buildAcknowledgements(thingId, adaptablePayloadValue);
    }

    private static List<Acknowledgement> buildAcknowledgements(final ThingId thingId, final JsonValue value) {
        return value.asObject()
                .stream()
                .map(field -> {
                    final JsonObjectBuilder builder = field.getValue().asObject().toBuilder();
                    builder.set(Acknowledgement.JsonFields.LABEL, field.getKey().toString())
                            .set(Acknowledgement.JsonFields.ENTITY_ID, thingId.toString())
                            .set(Acknowledgement.JsonFields.ENTITY_TYPE, ThingConstants.ENTITY_TYPE.toString());
                    if (!field.getValue().asObject().contains(Acknowledgement.JsonFields.DITTO_HEADERS.getPointer())) {
                        builder.set(Acknowledgement.JsonFields.DITTO_HEADERS, JsonObject.empty());
                    }
                    return Acknowledgement.fromJson(builder.build(), DittoHeaders.empty());
                })
                .collect(Collectors.toList());
    }

    private static HttpStatus getHttpStatusOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getHttpStatus()
                .orElseThrow(() -> new JsonMissingFieldException(Payload.JsonFields.STATUS));
    }

    @Override
    public Adaptable toAdaptable(final Acknowledgements acknowledgements, final TopicPath.Channel channel) {
        return Adaptable.newBuilder(getTopicPath(acknowledgements, channel))
                .withPayload(getPayload(acknowledgements))
                .withHeaders(getExternalHeaders(acknowledgements.getDittoHeaders()))
                .build();
    }

    @Override
    public TopicPath toTopicPath(final Acknowledgements acknowledgements, final TopicPath.Channel channel) {
        return getTopicPath(acknowledgements, channel);
    }

    @Override
    public Set<TopicPath.Group> getGroups() {
        return EnumSet.of(TopicPath.Group.THINGS);
    }

    @Override
    public Set<TopicPath.Channel> getChannels() {
        return EnumSet.of(TopicPath.Channel.TWIN, TopicPath.Channel.LIVE);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.ACKS);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return Collections.emptySet();
    }

    @Override
    public boolean isForResponses() {
        return true;
    }

    @Override
    public boolean requiresSubject() {
        return false;
    }

    private static TopicPath getTopicPath(final Acknowledgements acknowledgement, final TopicPath.Channel channel) {
        return AcknowledgementAdapter.getTopicPathBuilder(channel, acknowledgement.getEntityId())
                .aggregatedAcks()
                .build();
    }

    private static Payload getPayload(final Acknowledgements acknowledgements) {
        return Payload.newBuilder(JsonPointer.empty())
                .withStatus(acknowledgements.getHttpStatus())
                .withValue(getPayloadValue(acknowledgements))
                .build();
    }

    private static JsonObject getPayloadValue(final Acknowledgements acknowledgements) {
        return acknowledgements.stream()
                .map(ack -> JsonField.newInstance(ack.getLabel(), toJsonWithoutLabel(ack)))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static JsonObject toJsonWithoutLabel(final Acknowledgement ack) {
        final JsonObjectBuilder builder = JsonObject.newBuilder()
                .set(Acknowledgement.JsonFields.STATUS_CODE, ack.getHttpStatus().getCode());
        ack.getEntity().ifPresent(payload -> builder.set(Acknowledgement.JsonFields.PAYLOAD, payload));
        if (!ack.getDittoHeaders().isEmpty()) {
            builder.set(Acknowledgement.JsonFields.DITTO_HEADERS, ack.getDittoHeaders().toJson());
        }
        return builder.build();
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders acknowledgementHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(acknowledgementHeaders);
        return ProtocolFactory.newHeadersWithJsonContentType(externalHeaders);
    }

}
