/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.Acknowledgements;

/**
 * Adapter for mapping a {@link Acknowledgement} to and from an {@link Adaptable}.
 *
 * @since 1.1.0
 */
final class AcknowledgementAdapter extends AbstractAdapter<Acknowledgement> {

    private AcknowledgementAdapter(
            final Map<String, JsonifiableMapper<Acknowledgement>> mappingStrategies,
            final HeaderTranslator headerTranslator) {

        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new AcknowledgementAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static AcknowledgementAdapter of(final HeaderTranslator headerTranslator) {
        return new AcknowledgementAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<Acknowledgement>> mappingStrategies() {
        final Map<String, JsonifiableMapper<Acknowledgement>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(TopicPath.Criterion.ACKS.getName(), adaptable ->
                Acknowledgements.newAcknowledgement(acknowledgementLabelFrom(adaptable),
                        thingIdFrom(adaptable), statusCodeFrom(adaptable),
                        adaptable.getPayload().getValue().orElse(null),
                        dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static AcknowledgementLabel acknowledgementLabelFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return Acknowledgements.newLabel(topicPath.getSubject().orElseThrow(() ->
                UnknownTopicPathException.newBuilder(topicPath)
                        .description("Adaptable TopicPath for Acknowledgement did not contain required <subject> value")
                        .build()
        ));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return topicPath.getCriterion().getName(); // "acks" for TopicPath.Criterion.ACKS
    }

    @Override
    public Adaptable constructAdaptable(final Acknowledgement acknowledgement, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(
                ThingId.of(acknowledgement.getEntityId()));

        final AcknowledgementTopicPathBuilder acknowledgementTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            acknowledgementTopicPathBuilder = topicPathBuilder.twin().acks();
        } else if (channel == TopicPath.Channel.LIVE) {
            acknowledgementTopicPathBuilder = topicPathBuilder.live().acks();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
        acknowledgementTopicPathBuilder.label(acknowledgement.getLabel());

        final PayloadBuilder payloadBuilder = Payload.newBuilder(JsonPointer.empty())
                .withStatus(acknowledgement.getStatusCode());

        final Optional<JsonValue> value =
                acknowledgement.getEntity(acknowledgement.getDittoHeaders()
                        .getSchemaVersion()
                        .orElse(acknowledgement.getLatestSchemaVersion()));
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(acknowledgementTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(acknowledgement.getDittoHeaders()))
                .build();
    }

}
