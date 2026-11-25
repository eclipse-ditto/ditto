/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.acknowledgements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link AcknowledgementAggregatorActorStarter}.
 */
public final class AcknowledgementAggregatorActorStarterTest {

    private static ActorSystem actorSystem;

    private AcknowledgementAggregatorActorStarter underTest;

    @BeforeClass
    public static void setUpClass() {
        // the starter needs the cluster extension for the address of the aggregator actors
        actorSystem = ActorSystem.create("AcknowledgementAggregatorActorStarterTest", ConfigFactory.parseString(
                "pekko.actor.provider = cluster\n" +
                        "pekko.remote.artery.canonical.hostname = \"127.0.0.1\"\n" +
                        "pekko.remote.artery.canonical.port = 0"));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Before
    public void setUp() {
        underTest = AcknowledgementAggregatorActorStarter.of(actorSystem,
                Duration.ofSeconds(60),
                HeaderTranslator.empty(),
                null,
                List.of(),
                List.of());
    }

    @Test
    public void preprocessDoesNotCallPreprocessorIfInvalidHeaderHandlerReturnsNull() {
        final var signal = modifyAttribute(DittoHeaders.newBuilder()
                .timeout(Duration.ZERO)
                .responseRequired(true)
                .build());
        final var invalidHeader = new AtomicReference<DittoHeaderInvalidException>();

        final Object result = underTest.preprocess(signal,
                (s, shouldStart) -> {
                    throw new AssertionError("preprocessor must not be called for invalid headers");
                },
                exception -> {
                    invalidHeader.set(exception);
                    return null;
                });

        assertThat(result).isNull();
        assertTimeoutHeaderInvalid(invalidHeader.get());
    }

    @Test
    public void preprocessRejectsZeroTimeoutWithRequestedAcks() {
        final var signal = modifyAttribute(DittoHeaders.newBuilder()
                .timeout(Duration.ZERO)
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .build());
        final var invalidHeader = new AtomicReference<DittoHeaderInvalidException>();

        underTest.preprocess(signal,
                (s, shouldStart) -> {
                    throw new AssertionError("preprocessor must not be called for invalid headers");
                },
                exception -> {
                    invalidHeader.set(exception);
                    return null;
                });

        assertTimeoutHeaderInvalid(invalidHeader.get());
    }

    @Test
    public void preprocessCallsPreprocessorForZeroTimeoutWithoutResponse() {
        final var signal = modifyAttribute(DittoHeaders.newBuilder()
                .timeout(Duration.ZERO)
                .responseRequired(false)
                .build());
        final var preprocessed = new AtomicReference<Signal<?>>();

        underTest.preprocess(signal,
                (s, shouldStart) -> {
                    preprocessed.set(s);
                    return null;
                },
                exception -> {
                    throw new AssertionError("headers must be valid", exception);
                });

        assertThat(preprocessed.get()).isEqualTo(signal);
    }

    @Test
    public void startDoesNotForwardIfResponseConsumerReturnsNull() {
        final var signal = modifyAttribute(DittoHeaders.newBuilder()
                .timeout(Duration.ZERO)
                .responseRequired(true)
                .build());
        final var responseOrError = new AtomicReference<>();

        final Object result = underTest.start(signal,
                null,
                response -> {
                    responseOrError.set(response);
                    return null;
                },
                (s, ackregator) -> {
                    throw new AssertionError("aggregator must not be started for invalid headers");
                },
                s -> {
                    throw new AssertionError("signal must not be forwarded for invalid headers");
                });

        assertThat(result).isNull();
        assertThat(responseOrError.get()).isInstanceOf(DittoHeaderInvalidException.class);
        assertTimeoutHeaderInvalid((DittoHeaderInvalidException) responseOrError.get());
    }

    private static ModifyAttribute modifyAttribute(final DittoHeaders dittoHeaders) {
        return ModifyAttribute.of(ThingId.generateRandom(), JsonPointer.of("foo"), JsonValue.of("bar"),
                dittoHeaders);
    }

    private static void assertTimeoutHeaderInvalid(final DittoHeaderInvalidException exception) {
        assertThat(exception).isNotNull();
        assertThat(exception.getInvalidHeaderKey()).contains(DittoHeaderDefinition.TIMEOUT.getKey());
    }

}
