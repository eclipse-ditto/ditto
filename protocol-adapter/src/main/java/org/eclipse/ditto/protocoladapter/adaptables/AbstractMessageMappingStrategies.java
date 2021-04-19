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
package org.eclipse.ditto.protocoladapter.adaptables;

import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.DIRECTION;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.FEATURE_ID;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.STATUS_CODE;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.SUBJECT;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.THING_ID;

import java.util.Map;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.MessagePath;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.commands.messages.MessageDeserializer;

/**
 * Provides helper methods to map from {@link Adaptable}s to MessageCommands or MessageCommandResponses.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractMessageMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    protected AbstractMessageMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Creates a {@link Message} from the passed {@link Adaptable}.
     *
     * @param adaptable the Adaptable to created the Message from.
     * @return the Message.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     * @throws IllegalArgumentException if {@code adaptable}
     * <ul>
     * <li>has no headers,</li>
     * <li>contains headers with a value that did not represent its appropriate Java type or</li>
     * <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     * @throws org.eclipse.ditto.model.messages.MessagePayloadSizeTooLargeException if the message's payload is too
     * large
     */
    @SuppressWarnings("rawtypes")
    protected static Message messageFrom(final Adaptable adaptable) {
        final MessageHeaders messageHeaders = messageHeadersFrom(adaptable);

        // also validates message size
        final Message<?> deserializedMessage =
                MessageDeserializer.deserializeMessageFromHeadersAndPayload(messageHeaders,
                        adaptable.getPayload().getValue().orElse(null));

        return MessagesModelFactory.newMessageBuilder(messageHeaders)
                .payload(deserializedMessage.getPayload().orElse(null))
                .rawPayload(deserializedMessage.getRawPayload().orElse(null))
                .extra(adaptable.getPayload().getExtra().orElse(null))
                .build();
    }

    /**
     * Creates {@link MessageHeaders} from the passed {@link Adaptable}.
     *
     * @param adaptable the Adaptable to created the MessageHeaders from.
     * @return the MessageHeaders.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     * @throws IllegalArgumentException if {@code adaptable}
     * <ul>
     * <li>contains headers with a value that did not represent its appropriate Java type or</li>
     * <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     */
    protected static MessageHeaders messageHeadersFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = adaptable.getDittoHeaders().toBuilder();

        // these headers are used to store message attributes of Message that are not fields.
        // their content comes from elsewhere; overwrite message headers of the same names.
        dittoHeadersBuilder.putHeader(THING_ID.getKey(), topicPath.getNamespace() + ":" + topicPath.getEntityName());
        dittoHeadersBuilder.putHeader(SUBJECT.getKey(), topicPath.getSubject().orElse(""));
        final Payload payload = adaptable.getPayload();
        final MessagePath payloadPath = payload.getPath();
        payloadPath.getDirection()
                .ifPresent(direction -> dittoHeadersBuilder.putHeader(DIRECTION.getKey(), direction.name()));
        payloadPath.getFeatureId()
                .ifPresent(featureId -> dittoHeadersBuilder.putHeader(FEATURE_ID.getKey(), featureId));
        payload.getHttpStatus()
                .ifPresent(httpStatus -> dittoHeadersBuilder.putHeader(STATUS_CODE.getKey(),
                        String.valueOf(httpStatus.getCode())));
        final DittoHeaders newDittoHeaders = dittoHeadersBuilder.build();

        return MessagesModelFactory.newHeadersBuilder(newDittoHeaders).build();
    }

    /**
     * Get the HTTP status from the adaptable payload.
     *
     * @throws NullPointerException if the Adaptable payload does not contain a status.
     * @since 2.0.0
     */
    protected static HttpStatus getHttpStatus(final Adaptable adaptable) {
        final Payload payload = adaptable.getPayload();
        return payload.getHttpStatus()
                .orElseThrow(() -> new NullPointerException("The message did not contain a HTTP status!"));
    }

}
