/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.placeholders.ExpressionResolver;

/**
 * Context information for sending a MQTT Publish message to the broker.
 */
final class MqttPublishingContext {

    private final GenericMqttPublish genericMqttPublish;
    private final Signal<?> signal;
    @Nullable private final Target autoAckTarget;
    private final ExpressionResolver connectionIdResolver;
    private final CompletableFuture<SendResult> sendResultCompletableFuture;

    private MqttPublishingContext(final GenericMqttPublish genericMqttPublish,
            final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final ExpressionResolver connectionIdResolver) {

        this.genericMqttPublish = ConditionChecker.checkNotNull(genericMqttPublish, "genericMqttPublish");
        this.signal = ConditionChecker.checkNotNull(signal, "signal");
        this.autoAckTarget = autoAckTarget;
        this.connectionIdResolver = ConditionChecker.checkNotNull(connectionIdResolver, "connectionIdResolver");
        sendResultCompletableFuture = new CompletableFuture<>();
    }

    /**
     * Returns a new instance of {@code MqttPublishingContext} for the specified arguments.
     *
     * @param genericMqttPublish the MQTT Publish message sent to the broker.
     * @param signal the originating {@code Signal} from which {@code genericMqttPublish} was obtained from.
     * @param autoAckTarget the {@code Target} from which acknowledgements should automatically be produced and
     * delivered.
     * @param connectionIdResolver resolves the connection ID from the issued acknowledgement label of
     * {@code autoAckTarget} – if present.
     * @return the new instance.
     * @throws NullPointerException if any argument but {@code autoAckTarget} is {@code null}.
     */
    static MqttPublishingContext newInstance(final GenericMqttPublish genericMqttPublish,
            final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final ExpressionResolver connectionIdResolver) {

        return new MqttPublishingContext(genericMqttPublish, signal, autoAckTarget, connectionIdResolver);
    }

    GenericMqttPublish getGenericMqttPublish() {
        return genericMqttPublish;
    }

    DittoHeaders getSignalDittoHeaders() {
        return signal.getDittoHeaders();
    }

    Optional<Acknowledgement> getAutoAcknowledgement() {
        final Optional<Acknowledgement> result;
        if (null != autoAckTarget) {
            result = autoAckTarget.getIssuedAcknowledgementLabel()
                    .flatMap(ackLbl -> ConnectionValidator.resolveConnectionIdPlaceholder(connectionIdResolver, ackLbl))
                    .flatMap(ackLbl -> WithEntityId.getEntityId(signal)
                            .map(entityId -> Acknowledgement.of(ackLbl,
                                    entityId,
                                    HttpStatus.OK,
                                    getSignalDittoHeaders())));
        } else {
            result = Optional.empty();
        }
        return result;
    }

    CompletableFuture<SendResult> getSendResultCompletableFuture() {
        return sendResultCompletableFuture;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (MqttPublishingContext) o;
        return Objects.equals(genericMqttPublish, that.genericMqttPublish) &&
                Objects.equals(signal, that.signal) &&
                Objects.equals(sendResultCompletableFuture, that.sendResultCompletableFuture) &&
                Objects.equals(autoAckTarget, that.autoAckTarget) &&
                Objects.equals(connectionIdResolver, that.connectionIdResolver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genericMqttPublish,
                signal,
                sendResultCompletableFuture,
                autoAckTarget,
                connectionIdResolver);
    }

}
