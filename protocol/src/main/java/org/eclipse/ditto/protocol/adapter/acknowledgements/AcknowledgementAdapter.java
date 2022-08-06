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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabelExternalUseForbiddenException;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.AcknowledgementTopicPathBuilder;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;

/**
 * Adapter for mapping a {@link Acknowledgement} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
 *
 * @since 1.1.0
 */
final class AcknowledgementAdapter implements Adapter<Acknowledgement> {

    private final HeaderTranslator headerTranslator;

    private AcknowledgementAdapter(final HeaderTranslator headerTranslator) {
        this.headerTranslator = headerTranslator;
    }

    static AcknowledgementAdapter getInstance(final HeaderTranslator headerTranslator) {
        return new AcknowledgementAdapter(checkNotNull(headerTranslator, "headerTranslator"));
    }

    static AcknowledgementTopicPathBuilder getTopicPathBuilder(final TopicPath.Channel channel,
            final EntityId entityId) {

        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(ThingId.of(entityId));
        if (TopicPath.Channel.TWIN == channel) {
            topicPathBuilder.twin();
        } else if (TopicPath.Channel.LIVE == channel) {
            topicPathBuilder.live();
        } else {
            throw new IllegalArgumentException(MessageFormat.format("Unknown channel <{}>", channel));
        }
        return topicPathBuilder.acks();
    }

    @Override
    public Acknowledgement fromAdaptable(final Adaptable adaptable) {
        checkNotNull(adaptable, "adaptable");
        return ThingAcknowledgementFactory.newAcknowledgement(getAcknowledgementLabel(adaptable),
                getThingId(adaptable),
                getHttpStatusOrThrow(adaptable),
                adaptable.getDittoHeaders(),
                getPayloadValueOrNull(adaptable));
    }

    private static ThingId getThingId(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getEntityName());
    }

    private static HttpStatus getHttpStatusOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getHttpStatus()
                .orElseThrow(() -> new JsonMissingFieldException(Payload.JsonFields.STATUS));
    }

    private static AcknowledgementLabel getAcknowledgementLabel(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return topicPath.getSubject()
                .map(AcknowledgementLabel::of)
                .map(ackLabel -> {
                    if (DittoAcknowledgementLabel.contains(ackLabel)) {
                        throw new DittoAcknowledgementLabelExternalUseForbiddenException(ackLabel);
                    }
                    return ackLabel;
                })
                .orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPath)
                        .description("Adaptable TopicPath for Acknowledgement did not contain required <subject> value")
                        .build());
    }

    @Nullable
    private static JsonValue getPayloadValueOrNull(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getValue().orElse(null);
    }

    @Override
    public Adaptable toAdaptable(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        if (DittoAcknowledgementLabel.contains(acknowledgement.getLabel())) {
            throw new DittoAcknowledgementLabelExternalUseForbiddenException(acknowledgement.getLabel());
        }
        return Adaptable.newBuilder(getTopicPath(acknowledgement, channel))
                .withPayload(getPayload(acknowledgement))
                .withHeaders(getExternalHeaders(acknowledgement.getDittoHeaders()))
                .build();
    }

    @Override
    public TopicPath toTopicPath(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        return getTopicPath(acknowledgement, channel);
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
        return true;
    }

    private static TopicPath getTopicPath(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        return getTopicPathBuilder(channel, acknowledgement.getEntityId())
                .label(acknowledgement.getLabel())
                .build();
    }

    private static Payload getPayload(final Acknowledgement acknowledgement) {
        return Payload.newBuilder(JsonPointer.empty())
                .withStatus(acknowledgement.getHttpStatus())
                .withValue(acknowledgement.getEntity(acknowledgement.getImplementedSchemaVersion())
                        .orElse(null))
                .build();
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders acknowledgementHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(acknowledgementHeaders);
        return DittoHeaders.of(externalHeaders);
    }

}
