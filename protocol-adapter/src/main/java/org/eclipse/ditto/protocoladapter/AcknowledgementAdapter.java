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
package org.eclipse.ditto.protocoladapter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.Acknowledgements;

/**
 * Adapter for mapping a {@link Acknowledgement} to and from an {@link Adaptable}.
 *
 * @since 1.1.0
 */
final class AcknowledgementAdapter implements Adapter<Acknowledgement> {

    private final HeaderTranslator headerTranslator;

    private AcknowledgementAdapter(final HeaderTranslator headerTranslator) {
        this.headerTranslator = headerTranslator;
    }

    public static AcknowledgementAdapter getInstance(final HeaderTranslator headerTranslator) {
        return new AcknowledgementAdapter(checkNotNull(headerTranslator, "headerTranslator"));
    }

    @Override
    public Acknowledgement fromAdaptable(final Adaptable adaptable) {
        checkNotNull(adaptable, "adaptable");
        return Acknowledgements.newAcknowledgement(getAcknowledgementLabel(adaptable),
                getThingId(adaptable),
                getStatusCodeOrThrow(adaptable),
                getPayloadValueOrNull(adaptable),
                adaptable.getDittoHeaders());
    }

    private static ThingId getThingId(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return ThingId.of(topicPath.getNamespace(), topicPath.getId());
    }

    private static HttpStatusCode getStatusCodeOrThrow(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getStatus()
                .orElseThrow(() -> new JsonMissingFieldException(Payload.JsonFields.STATUS));
    }

    private static AcknowledgementLabel getAcknowledgementLabel(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return topicPath.getSubject()
                .map(AcknowledgementLabel::of)
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
        return Adaptable.newBuilder(getTopicPath(acknowledgement, channel))
                .withPayload(getPayload(acknowledgement))
                .withHeaders(getExternalHeaders(acknowledgement.getDittoHeaders()))
                .build();
    }

    private static TopicPath getTopicPath(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = TopicPath.newBuilder(ThingId.of(acknowledgement.getEntityId()));
        if (TopicPath.Channel.TWIN == channel) {
            topicPathBuilder.twin();
        } else if (TopicPath.Channel.LIVE == channel) {
            topicPathBuilder.live();
        } else {
            throw new IllegalArgumentException(MessageFormat.format("Unknown channel <{}>", channel));
        }
        return topicPathBuilder.acks()
                .label(acknowledgement.getLabel())
                .build();
    }

    private static Payload getPayload(final Acknowledgement acknowledgement) {
        return Payload.newBuilder(JsonPointer.empty())
                .withStatus(acknowledgement.getStatusCode())
                .withValue(acknowledgement.getEntity(acknowledgement.getDittoHeaders()
                        .getSchemaVersion()
                        .orElseGet(acknowledgement::getLatestSchemaVersion))
                        .orElse(null))
                .build();
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders acknowledgementHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(acknowledgementHeaders);
        return ProtocolFactory.newHeadersWithDittoContentType(externalHeaders);
    }

}
