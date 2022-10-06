/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.eclipse.ditto.connectivity.service.messaging.amqp.JmsExceptionThrowingBiConsumer.wrap;
import static org.eclipse.ditto.connectivity.service.messaging.amqp.JmsExceptionThrowingFunction.wrap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.akka.logging.CommonMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.MdcEntrySettable;

import akka.event.LoggingAdapter;

/**
 * Converts between headers and AMQP 1.0 properties and application properties.
 * <ul>
 * <li>Headers matching AMQP 1.0 properties defined in ss. 3.2.4 of the specification are read from and written into
 * headers literally.</li>
 * <li>Headers prefixed by {@code amqp.application.property:} are read and written into application properties.</li>
 * <li>Headers not matching AMQP 1.0 properties and not prefixed by {@code amqp.application.property} are read from
 * and written into headers literally.</li>
 * </ul>
 */
final class JMSPropertyMapper {

    private static final Map<String, BiConsumer<Message, String>> AMQP_PROPERTY_SETTER = createAmqpPropertySetter();

    private static final class AMQP {

        private static final String APPLICATION_PROPERTY_PREFIX = "amqp.application.property:";
        private static final String MESSAGE_ANNOTATION_PREFIX = "amqp.message.annotation:";

        // 13 defined properties in total
        private static final String MESSAGE_ID = "message-id";
        private static final String USER_ID = "user-id";
        private static final String TO = "to";
        private static final String SUBJECT = "subject";
        private static final String REPLY_TO = "reply-to";
        private static final String CORRELATION_ID = "correlation-id";
        private static final String CONTENT_TYPE = "content-type";
        private static final String CONTENT_ENCODING = "content-encoding";
        private static final String ABSOLUTE_EXPIRY_TIME = "absolute-expiry-time";
        private static final String CREATION_TIME = "creation-time";
        private static final String GROUP_ID = "group-id";
        private static final String GROUP_SEQUENCE = "group-sequence";
        private static final String REPLY_TO_GROUP_ID = "reply-to-group-id";
    }

    private static Map<String, BiConsumer<Message, String>> createAmqpPropertySetter() {
        final HashMap<String, BiConsumer<Message, String>> map = new HashMap<>();
        map.put(AMQP.MESSAGE_ID, JMSPropertyMapper::setMessageId);
        map.put(AMQP.USER_ID, JMSPropertyMapper::setUserId);
        map.put(AMQP.TO, JMSPropertyMapper::setTo);
        map.put(AMQP.SUBJECT, JMSPropertyMapper::setSubject);
        map.put(AMQP.REPLY_TO, JMSPropertyMapper::setReplyTo);
        map.put(AMQP.CORRELATION_ID, JMSPropertyMapper::setCorrelationId);
        map.put(AMQP.CONTENT_TYPE, JMSPropertyMapper::setContentType);
        map.put(AMQP.CONTENT_ENCODING, JMSPropertyMapper::setContentEncoding);
        map.put(AMQP.ABSOLUTE_EXPIRY_TIME, JMSPropertyMapper::setAbsoluteExpiryTime);
        map.put(AMQP.CREATION_TIME, JMSPropertyMapper::setCreationTime);
        map.put(AMQP.GROUP_ID, JMSPropertyMapper::setGroupId);
        map.put(AMQP.GROUP_SEQUENCE, JMSPropertyMapper::setGroupSequence);
        map.put(AMQP.REPLY_TO_GROUP_ID, JMSPropertyMapper::setReplyToGroupId);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Set headers as properties and application properties into an AMQP JMS message according to the rules in the
     * class javadoc.
     *
     * @param message the message.
     * @param headers the mapped headers.
     * @param logger the logger.
     */
    static <T extends LoggingAdapter & MdcEntrySettable<T>> void setPropertiesAndApplicationProperties(
            final Message message, final Map<String, String> headers, final T logger) {

        headers.forEach((headerName, headerValue) -> {
            try {
                if (isDefinedAmqpProperty(headerName)) {
                    setDefinedAmqpProperty(message, headerName, headerValue);
                } else if (isMessageAnnotation(headerName)) {
                    setMessageAnnotation(message, headerName, headerValue);
                } else {
                    setAmqpApplicationProperty(message, headerName, headerValue);
                }
            } catch (final Exception e) {

                // Errors are mostly caused by user; thus log them at debug level then proceed with other properties.
                if (logger.isDebugEnabled()) {
                    @Nullable final String correlationId = headers.get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    logger.withMdcEntry(CommonMdcEntryKey.CORRELATION_ID, correlationId)
                            .debug("Error setting AMQP property/application property <{}>=<{}>: <{}>", headerName,
                                    headerValue, e);
                }
            }
        });
    }

    /**
     * Extract headers from properties, application properties and message annotations of an AMQP JMS message to be
     * mapped.
     *
     * @param message the message.
     * @return the headers to be mapped.
     */
    static Map<String, String> getHeadersFromProperties(final Message message) {
        final Map<String, String> headers = new HashMap<>();
        readDefinedAmqpPropertiesFromMessage(message, headers);
        readAmqpApplicationPropertiesFromMessage(message, headers);
        readAmqpMessageAnnotationsFromMessage(message, headers);
        return Collections.unmodifiableMap(headers);
    }

    private static void setMessageId(final Message message, final String messageId) {
        wrap(Message::setJMSMessageID).accept(message, messageId);
    }

    private static Optional<String> getMessageId(final Message message) {
        return Optional.ofNullable(wrap(Message::getJMSMessageID).apply(message));
    }

    private static void setUserId(final Message message, final String userId) {
        wrapFacadeBiConsumer(message, userId, AmqpJmsMessageFacade::setUserId);
    }

    private static Optional<String> getUserId(final Message message) {
        return Optional.ofNullable(wrapFacadeFunction(message, AmqpJmsMessageFacade::getUserId));
    }

    private static void setTo(final Message message, final String to) {
        wrap(Message::setJMSDestination).accept(message, asDestination(to));
    }

    private static void setSubject(final Message message, final String subject) {
        wrap(Message::setJMSType).accept(message, subject);
    }

    private static Optional<String> getSubject(final Message message) {
        return Optional.ofNullable(wrap(Message::getJMSType).apply(message));
    }

    private static Optional<String> getTo(final Message message) {
        return Optional.ofNullable(wrap(Message::getJMSDestination).apply(message)).map(Destination::toString);
    }

    private static void setReplyTo(final Message message, final String replyTo) {
        wrap(Message::setJMSReplyTo).accept(message, asDestination(replyTo));
    }

    private static Optional<String> getReplyTo(final Message message) {
        return Optional.ofNullable(wrap(Message::getJMSReplyTo).apply(message)).map(Destination::toString);
    }

    private static void setCorrelationId(final Message message, final String correlationId) {
        wrap(Message::setJMSCorrelationID).accept(message, correlationId);
    }

    private static Optional<String> getCorrelationId(final Message message) {
        return Optional.ofNullable(wrap(Message::getJMSCorrelationID).apply(message));
    }

    private static void setContentType(final Message message, final String contentType) {
        wrapFacadeBiConsumer(message, Symbol.valueOf(contentType), AmqpJmsMessageFacade::setContentType);
    }

    private static Optional<String> getContentType(final Message message) {
        return Optional.ofNullable(wrapFacadeFunction(message, AmqpJmsMessageFacade::getContentType))
                .map(Symbol::toString);
    }

    private static void setContentEncoding(final Message message, final String contentEncoding) {
        // do nothing---not supported by Qpid client.
    }

    private static Optional<String> getContentEncoding(final Message message) {
        // return nothing---not supported by Qpid client.
        return Optional.empty();
    }

    private static void setAbsoluteExpiryTime(final Message message, final String absoluteExpiryTime) {
        wrap(Message::setJMSExpiration).accept(message, Long.valueOf(absoluteExpiryTime));
    }

    private static Optional<String> getAbsoluteExpiryTime(final Message message) {
        return nonZeroToString(wrap(Message::getJMSExpiration).apply(message));
    }

    private static void setCreationTime(final Message message, final String creationTime) {
        wrap(Message::setJMSTimestamp).accept(message, Long.valueOf(creationTime));
    }

    private static Optional<String> getCreationTime(final Message message) {
        return nonZeroToString(wrap(Message::getJMSTimestamp).apply(message));
    }

    private static void setGroupId(final Message message, final String groupId) {
        wrapFacadeBiConsumer(message, groupId, AmqpJmsMessageFacade::setGroupId);
    }

    private static Optional<String> getGroupId(final Message message) {
        return Optional.ofNullable(wrapFacadeFunction(message, AmqpJmsMessageFacade::getGroupId));
    }

    private static void setGroupSequence(final Message message, final String groupSequence) {
        wrapFacadeBiConsumer(message, Integer.valueOf(groupSequence), AmqpJmsMessageFacade::setGroupSequence);
    }

    private static Optional<String> getGroupSequence(final Message message) {
        return Optional.ofNullable(wrapFacadeFunction(message, AmqpJmsMessageFacade::getGroupSequence))
                .flatMap(JMSPropertyMapper::nonZeroToString);
    }

    private static void setReplyToGroupId(final Message message, final String groupId) {
        wrapFacadeBiConsumer(message, groupId, AmqpJmsMessageFacade::setReplyToGroupId);
    }

    private static Optional<String> getReplyToGroupId(final Message message) {
        return Optional.ofNullable(wrapFacadeFunction(message, AmqpJmsMessageFacade::getReplyToGroupId));
    }

    private static Destination asDestination(final String address) {
        return AmqpTarget.fromTargetAddress(address).getJmsDestination();
    }

    private static JMSRuntimeException amqpJmsMessageFacadeTypeError() {
        return new JMSRuntimeException("Message is not a JmsMessage or its facade is not an AmqpJmsMessageFacade");
    }

    private static <T> void wrapFacadeBiConsumer(final Message message, final T arg,
            final BiConsumer<AmqpJmsMessageFacade, T> consumer) {
        wrapFacadeFunction(message, amqpJmsMessageFacade -> {
            consumer.accept(amqpJmsMessageFacade, arg);
            return null;
        });
    }

    @Nullable
    private static <T> T wrapFacadeFunction(final Message message, final Function<AmqpJmsMessageFacade, T> function) {
        if (message instanceof JmsMessage jmsMessage) {
            final JmsMessageFacade facade = jmsMessage.getFacade();
            if (facade instanceof AmqpJmsMessageFacade amqpJmsMessageFacade) {
                return function.apply(amqpJmsMessageFacade);
            }
        }
        throw amqpJmsMessageFacadeTypeError();
    }

    private static Optional<String> nonZeroToString(final long signedInteger) {
        // if an integral property is not set, Qpid sets it to 0.
        // negative integers are allowed because they may represent big unsigned integers.
        if (signedInteger == 0L) {
            return Optional.empty();
        } else {
            return Optional.of(String.valueOf(signedInteger));
        }
    }

    private static boolean isDefinedAmqpProperty(final String name) {
        return AMQP_PROPERTY_SETTER.containsKey(name);
    }

    private static boolean isMessageAnnotation(final String name) {
        return name.startsWith(AMQP.MESSAGE_ANNOTATION_PREFIX);
    }

    private static Consumer<String> set(final Map<String, String> modifiableHeaders, final String name) {
        return value -> modifiableHeaders.put(name, value);
    }

    // precondition: isDefinedAmqpProperty(name)
    private static void setDefinedAmqpProperty(final Message message, final String name, final String value) {
        AMQP_PROPERTY_SETTER.get(name).accept(message, value);
    }

    // precondition: isMessageAnnotation(name)
    private static void setMessageAnnotation(final Message message, final String name, final String value) {
        final String applicationPropertyName = name.substring(AMQP.MESSAGE_ANNOTATION_PREFIX.length());

        wrapFacadeBiConsumer(message, value, (facade, v) -> wrap((x, y) ->
                facade.setTracingAnnotation(applicationPropertyName, value)).accept(message, value));
    }

    private static void setAmqpApplicationProperty(final Message message, final String name, final String value) {
        final String applicationPropertyName = name.startsWith(AMQP.APPLICATION_PROPERTY_PREFIX)
                ? name.substring(AMQP.APPLICATION_PROPERTY_PREFIX.length())
                : name;

        wrapFacadeBiConsumer(message, value, (facade, v) -> wrap((x, y) ->
                facade.setApplicationProperty(applicationPropertyName, value)).accept(message, value));
    }

    // precondition: headers are mutable
    private static void readDefinedAmqpPropertiesFromMessage(final Message message, final Map<String, String> headers) {
        // 13 properties in total
        getMessageId(message).ifPresent(set(headers, AMQP.MESSAGE_ID));
        getUserId(message).ifPresent(set(headers, AMQP.USER_ID));
        getTo(message).ifPresent(set(headers, AMQP.TO));
        getSubject(message).ifPresent(set(headers, AMQP.SUBJECT));
        getReplyTo(message).ifPresent(set(headers, AMQP.REPLY_TO));
        getCorrelationId(message).ifPresent(set(headers, AMQP.CORRELATION_ID));
        getContentType(message).ifPresent(set(headers, AMQP.CONTENT_TYPE));
        getContentEncoding(message).ifPresent(set(headers, AMQP.CONTENT_ENCODING));
        getAbsoluteExpiryTime(message).ifPresent(set(headers, AMQP.ABSOLUTE_EXPIRY_TIME));
        getCreationTime(message).ifPresent(set(headers, AMQP.CREATION_TIME));
        getGroupId(message).ifPresent(set(headers, AMQP.GROUP_ID));
        getGroupSequence(message).ifPresent(set(headers, AMQP.GROUP_SEQUENCE));
        getReplyToGroupId(message).ifPresent(set(headers, AMQP.REPLY_TO_GROUP_ID));
    }

    // precondition: headers are mutable
    private static void readAmqpApplicationPropertiesFromMessage(final Message message,
            final Map<String, String> theHeaders) {

        wrapFacadeBiConsumer(message, theHeaders, (facade, headers) -> {
            for (final String applicationProperty : facade.getApplicationPropertyNames(new HashSet<>())) {
                final Object value = wrap(msg -> facade.getApplicationProperty(applicationProperty)).apply(message);
                if (value != null) {
                    final String headerName = isDefinedAmqpProperty(applicationProperty)
                            ? AMQP.APPLICATION_PROPERTY_PREFIX + applicationProperty
                            : applicationProperty;
                    headers.put(headerName, value.toString());
                }
            }
        });
    }

    private static void readAmqpMessageAnnotationsFromMessage(final Message message,
            final Map<String, String> theHeaders) {

        wrapFacadeBiConsumer(message, theHeaders, (facade, headers) -> {
            wrap(msg -> {
                facade.filterTracingAnnotations((key, value) -> {
                    if (value != null) {
                        final var headerName = AMQP.MESSAGE_ANNOTATION_PREFIX + key;
                        headers.put(headerName, value.toString());
                    }
                });
                return null;
            }).apply(message);
        });
    }

    private JMSPropertyMapper() {}
}
