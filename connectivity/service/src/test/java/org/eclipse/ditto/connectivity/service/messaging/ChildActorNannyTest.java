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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.event.LoggingAdapter;

/**
 * Unit test for {@link ChildActorNanny}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ChildActorNannyTest {

    private static final String ACTOR_NAME = "myActor";

    @Mock
    private ActorRefFactory actorRefFactory;

    @Mock
    private LoggingAdapter logger;

    @Test
    public void newInstanceWithNullActorRefFactoryThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ChildActorNanny.newInstance(null, logger))
                .withMessage("The actorRefFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullLoggingAdapterThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ChildActorNanny.newInstance(actorRefFactory, null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void startChildActorWithNullActorNameThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.startChildActor(null, Props.empty()))
                .withMessage("The actorName must not be null!")
                .withNoCause();
    }

    @Test
    public void startChildActorWithEmptyActorNameThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.startChildActor("", Props.empty()))
                .withMessage("The argument 'actorName' must not be empty!")
                .withNoCause();
    }

    @Test
    public void startChildActorWithNullPropsThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.startChildActor(ACTOR_NAME, null))
                .withMessage("The actorProps must not be null!")
                .withNoCause();
    }

    @Test
    public void startChildActorWithValidArgumentsInvokesActorRefFactory() {
        final var actorProps = Props.empty();
        final var actorRef = Mockito.mock(ActorRef.class);
        Mockito.when(actorRefFactory.actorOf(Mockito.eq(actorProps), Mockito.eq(ACTOR_NAME))).thenReturn(actorRef);
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThat(underTest.startChildActor(ACTOR_NAME, actorProps)).isEqualTo(actorRef);
    }

    @Test
    public void startChildActorEncodesActorName() {
        final var actorNameArgumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.when(actorRefFactory.actorOf(Mockito.any(), actorNameArgumentCaptor.capture()))
                .thenReturn(Mockito.mock(ActorRef.class));
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        underTest.startChildActor("mü%Äctor", Props.empty());

        assertThat(actorNameArgumentCaptor.getValue()).isEqualTo("m%3F%25%3Fctor");
    }

    @Test
    public void startChildActorConflictFreeWithNullActorNameThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.startChildActorConflictFree(null, Props.empty()))
                .withMessage("The baseActorName must not be null!")
                .withNoCause();
    }

    @Test
    public void startChildActorConflictFreeWithEmptyActorNameThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.startChildActorConflictFree("", Props.empty()))
                .withMessage("The argument 'baseActorName' must not be empty!")
                .withNoCause();
    }

    @Test
    public void startChildActorConflictFreeWithNullPropsThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.startChildActorConflictFree(ACTOR_NAME, null))
                .withMessage("The actorProps must not be null!")
                .withNoCause();
    }

    @Test
    public void startChildActorConflictFreeGeneratesExpectedActorNames() {
        final var otherActorName = "anotherActor";
        final var invocationCountOtherActor = 7;
        final var actorNameArgumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.when(actorRefFactory.actorOf(Mockito.any(), actorNameArgumentCaptor.capture()))
                .thenReturn(Mockito.mock(ActorRef.class));
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        underTest.startChildActorConflictFree(ACTOR_NAME, Props.empty());
        IntStream.range(0, invocationCountOtherActor)
                .forEach(_unused -> underTest.startChildActorConflictFree(otherActorName, Props.empty()));

        assertThat(actorNameArgumentCaptor.getAllValues())
                .containsExactlyElementsOf(Stream.concat(
                        Stream.of(ACTOR_NAME + 1),
                        IntStream.rangeClosed(1, invocationCountOtherActor).mapToObj(count -> otherActorName + count)
                ).toList());
    }

    @Test
    public void stopChildActorWithNullActorRefThrowsException() {
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.stopChildActor(null))
                .withMessage("The childActorRef must not be null!")
                .withNoCause();
    }

    @Test
    public void stopChildActorInvokesActorRefFactory() {
        final var childActorRef = Mockito.mock(ActorRef.class);
        final var underTest = ChildActorNanny.newInstance(actorRefFactory, logger);

        underTest.stopChildActor(childActorRef);

        Mockito.verify(actorRefFactory).stop(childActorRef);
    }

}