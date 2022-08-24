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
package org.eclipse.ditto.edge.service.acknowledgements.things;

import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;

import java.util.Optional;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.CommandResponseAcknowledgementProvider;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Provides Acknowledgements specific for {@link ThingCommandResponse}s.
 *
 * @since 3.0.0
 */
public final class ThingCommandResponseAcknowledgementProvider
        implements CommandResponseAcknowledgementProvider<ThingCommand<?>> {

    private static final ThingCommandResponseAcknowledgementProvider
            INSTANCE = new ThingCommandResponseAcknowledgementProvider();

    /**
     * Returns an instance of {@code ThingCommandResponseAcknowledgementProvider}.
     *
     * @return the instance.
     */
    public static ThingCommandResponseAcknowledgementProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public Acknowledgement provideAcknowledgement(final ThingCommand<?> originatingSignal,
            final CommandResponse<?> commandResponse) {

        if (commandResponse instanceof ThingCommandResponse<?> thingCommandResponse) {

            final AcknowledgementLabel acknowledgementLabel = getAckLabelOfResponse(originatingSignal);
            return ThingAcknowledgementFactory.newAcknowledgement(acknowledgementLabel,
                    thingCommandResponse.getEntityId(),
                    thingCommandResponse.getHttpStatus(),
                    thingCommandResponse.getDittoHeaders(),
                    getPayload(thingCommandResponse).orElse(null));
        } else {
            throw DittoInternalErrorException.newBuilder()
                    .dittoHeaders(originatingSignal.getDittoHeaders())
                    .cause(new IllegalArgumentException("The given commandResponse was not of type" +
                            " ThingCommandResponse. Check by isApplicable before passing a command response to this " +
                            "method."))
                    .build();
        }
    }

    @Override
    public boolean isApplicable(final CommandResponse<?> commandResponse) {
        return commandResponse instanceof ThingCommandResponse<?>;
    }

    @Override
    public Class<?> getCommandClass() {
        return ThingCommand.class;
    }

    private static AcknowledgementLabel getAckLabelOfResponse(final Signal<?> signal) {
        // check originating signal for ack label of response
        // because live commands may generate twin responses due to live timeout fallback strategy
        // smart channel commands are treated as live channel commands
        final boolean isChannelLive = Signal.isChannelLive(signal);
        final boolean isChannelSmart = Signal.isChannelSmart(signal);
        return isChannelLive || isChannelSmart ? LIVE_RESPONSE : TWIN_PERSISTED;
    }

    private static Optional<JsonValue> getPayload(final ThingCommandResponse<?> thingCommandResponse) {
        final Optional<JsonValue> result;
        if (thingCommandResponse instanceof WithOptionalEntity) {
            final WithOptionalEntity withOptionalEntity = (WithOptionalEntity) thingCommandResponse;
            result = withOptionalEntity.getEntity(thingCommandResponse.getImplementedSchemaVersion());
        } else {
            result = Optional.empty();
        }

        return result;
    }
}
