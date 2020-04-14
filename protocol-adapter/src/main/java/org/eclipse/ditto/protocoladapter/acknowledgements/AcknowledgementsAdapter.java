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
package org.eclipse.ditto.protocoladapter.acknowledgements;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingConstants;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementsFactory;

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

    public static AcknowledgementsAdapter getInstance(final HeaderTranslator headerTranslator) {
        return new AcknowledgementsAdapter(checkNotNull(headerTranslator, "headerTranslator"));
    }

    @Override
    public Acknowledgements fromAdaptable(final Adaptable adaptable) {
        checkNotNull(adaptable, "adaptable");
        final ThingId thingId = getThingId(adaptable);
        return ThingAcknowledgementsFactory.newAcknowledgements(
                thingId,
                gatherContainedAcknowledgements(adaptable, thingId),
                getStatusCodeOrThrow(adaptable),
                adaptable.getDittoHeaders()
        );
    }

    private static ThingId getThingId(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getId());
    }

    private static List<Acknowledgement> gatherContainedAcknowledgements(final Adaptable adaptable,
            final ThingId thingId) {

        final JsonValue adaptablePayloadValue = adaptable.getPayload().getValue().orElse(JsonValue.nullLiteral());
        return buildAcknowledgements(adaptable, thingId, adaptablePayloadValue);
    }

    private static List<Acknowledgement> buildAcknowledgements(final Adaptable adaptable, final ThingId thingId,
            final JsonValue value) {

        if (value.isNull()) {
            return Collections.singletonList(buildSingleAcknowledgement(adaptable, null));
        } else if (value.isObject()) {
            return value.asObject().stream().map(field -> {
                if (filterForAcknowledgementJsonObject(field)) {
                    return ThingAcknowledgementFactory.fromJson(
                            field.getValue().asObject().toBuilder()
                                    .set(Acknowledgement.JsonFields.LABEL, field.getKey().toString())
                                    .set(Acknowledgement.JsonFields.ENTITY_ID, thingId.toString())
                                    .set(Acknowledgement.JsonFields.ENTITY_TYPE, ThingConstants.ENTITY_TYPE.toString())
                                    .build()
                    );
                } else {
                    return buildSingleAcknowledgement(adaptable, field);
                }
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private static Acknowledgement buildSingleAcknowledgement(final Adaptable adaptable,
            @Nullable final JsonField field) {
        return ThingAcknowledgementFactory.newAcknowledgement(
                getLabelInCaseOfSingleAcknowledgement(adaptable)
                        .orElseGet(() -> AcknowledgementLabel.of(checkNotNull(field, "field").getKey())),
                getThingId(adaptable),
                getStatusCodeOrThrow(adaptable),
                adaptable.getDittoHeaders(),
                null != field ? JsonObject.newBuilder().set(field).build() : null
        );
    }

    private static Optional<AcknowledgementLabel> getLabelInCaseOfSingleAcknowledgement(final Adaptable adaptable) {
        final JsonPointer pathJsonPointer = adaptable.getPayload().getPath();
        if (pathJsonPointer.isEmpty()) {
            return Optional.empty();
        } else {
            String path = adaptable.getPayload().getPath().toString();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return Optional.of(AcknowledgementLabel.of(path));
        }
    }

    private static boolean filterForAcknowledgementJsonObject(final JsonField field) {
        return field.getValue().isObject() &&
                field.getValue().asObject().contains(Acknowledgement.JsonFields.STATUS_CODE.getPointer()) &&
                field.getValue().asObject().contains(Acknowledgement.JsonFields.DITTO_HEADERS.getPointer());
    }

    private static HttpStatusCode getStatusCodeOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getStatus()
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
        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(ThingId.of(acknowledgement.getEntityId()));
        if (TopicPath.Channel.TWIN == channel) {
            topicPathBuilder.twin();
        } else if (TopicPath.Channel.LIVE == channel) {
            topicPathBuilder.live();
        } else {
            throw new IllegalArgumentException(MessageFormat.format("Unknown channel <{}>", channel));
        }
        return topicPathBuilder.acks()
                .aggregatedAcks()
                .build();
    }

    private static Payload getPayload(final Acknowledgements acknowledgements) {
        final JsonPointer path;
        if (acknowledgements.getSize() == 1) {
            path = JsonPointer.of(acknowledgements.stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Stream did not contain any Acknowledgements but " +
                            "should have"))
                    .getLabel());
        } else {
            path = JsonPointer.empty();
        }
        return Payload.newBuilder(path)
                .withStatus(acknowledgements.getStatusCode())
                .withValue(acknowledgements.getEntity(acknowledgements.getImplementedSchemaVersion())
                        .orElse(null))
                .build();
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders acknowledgementHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(acknowledgementHeaders);
        if (externalHeaders.containsKey(DittoHeaderDefinition.CONTENT_TYPE.getKey())) {
            return DittoHeaders.of(externalHeaders);
        }
        return ProtocolFactory.newHeadersWithDittoContentType(externalHeaders);
    }

}
