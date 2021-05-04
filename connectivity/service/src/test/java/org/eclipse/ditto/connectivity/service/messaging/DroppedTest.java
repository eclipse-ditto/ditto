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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link Dropped}.
 */
public final class DroppedTest {

    @Test
    public void createInstanceWithNullSendingContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Dropped(null, "Signal dropped, target address unresolved: {0}"))
                .withMessage("The sendingContext must not be null!")
                .withNoCause();
    }

    @Test
    public void monitorAndAcknowledge() {
        final ConnectionMonitor droppedMonitor = Mockito.mock(ConnectionMonitor.class);
        final OutboundSignal.Mapped mappedOutboundSignal = Mockito.mock(OutboundSignal.Mapped.class);
        final Signal source = Mockito.mock(Signal.class);
        Mockito.when(mappedOutboundSignal.getSource()).thenReturn(source);
        final GenericTarget genericTarget = Mockito.mock(GenericTarget.class);
        final String address = "742 Evergreen Terrace";
        Mockito.when(genericTarget.getAddress()).thenReturn(address);
        final SendingContext sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(Mockito.mock(ExternalMessage.class))
                .genericTarget(genericTarget)
                .publishedMonitor(Mockito.mock(ConnectionMonitor.class))
                .acknowledgedMonitor(Mockito.mock(ConnectionMonitor.class))
                .droppedMonitor(droppedMonitor)
                .autoAckTarget(Mockito.mock(Target.class))
                .build();

        final Dropped underTest = new Dropped(sendingContext, "Signal dropped, target address unresolved: {0}");

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(null);

        assertThat(result).isEmpty();
        Mockito.verify(droppedMonitor).success(source, "Signal dropped, target address unresolved: {0}", address);
    }

}
