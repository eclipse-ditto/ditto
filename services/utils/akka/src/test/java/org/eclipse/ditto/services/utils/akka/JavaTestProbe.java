/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.japi.Util;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import scala.collection.Seq;
import scala.concurrent.duration.Duration;

/**
 * Wrapper of {@link akka.testkit.TestProbe}. Exposes methods of {@link TestProbe} not available in {@link
 * akka.testkit.JavaTestKit}. For example, it exports {@link TestProbe#expectMsgAllClassOf(Seq)} to intercept
 * messages that may arrive simultaneously.
 * <p>
 * Usage similar to {@link akka.testkit.JavaTestKit}.
 * <p>
 * <pre>
 * new JavaTestProbe(actorSystem) {{
 *     final ActorRef underTest = newActorUnderTest();
 *     underTest.tell(new Message());
 *     expectMsgAllClass()
 *       .of(Reply.class, Test::assertReplyIsCorrect)
 *       .of(Event.class, Test::assertEventIsCorrect)
 *       .run();
 * }}
 * </pre>
 */
public class JavaTestProbe {

    private final TestProbe testProbe;

    /**
     * Creates a {@code JavaTestProbe} by wrapping the Scala test probe.
     *
     * @param testProbe The test probe to wrap around.
     */
    public JavaTestProbe(final TestProbe testProbe) {
        this.testProbe = testProbe;
    }

    /**
     * Creates a new test probe directly in the actor system.
     *
     * @param actorSystem The actor system to create the probe in.
     */
    public JavaTestProbe(final ActorSystem actorSystem) {
        this.testProbe = new TestProbe(actorSystem);
    }

    /**
     * @return The reference of the actor wrapped by this probe.
     */
    public ActorRef ref() {
        return testProbe.ref();
    }

    /**
     * Returns the underlying Scala test probe in case some of its functionalities are neeeded but not are not exported
     * here.
     *
     * @return the underlying Scala test probe.
     */
    public TestProbe getScalaTestProbe() {
        return testProbe;
    }

    /**
     * Same as {@link JavaTestKit#expectMsgClass(Class)}.
     *
     * @param clazz The class to match against.
     * @return The received message belong to {@code clazz}.
     */
    public <T> T expectMsgClass(final Class<T> clazz) {
        return testProbe.expectMsgClass(clazz);
    }

    /**
     * Same as {@link JavaTestKit#expectMsgClass(Class)}, except a handler is invoked any successfully matched message.
     *
     * @param clazz The class to match against.
     * @param handler What to do in case of a match.
     * @return The received message belong to {@code clazz}.
     */
    public <T> T expectMsgClass(final Class<T> clazz, final Consumer<T> handler) {
        final T message = expectMsgClass(clazz);
        handler.accept(message);
        return message;
    }

    /**
     * Same as {@link JavaTestKit#expectNoMsg()}.
     */
    public void expectNoMsg() {
        testProbe.expectNoMsg();
    }

    /**
     * Java-friendly interface of {@link TestProbe#expectMsgAllClassOf(Seq)}.
     * <p>
     * Usage example:
     * <p>
     * <pre>
     * javaTestProbe.expectMsgAllClass()
     *   .of(RetrieveThingResponse.class, this::assertRetrieveThingResponseIsAsExpected)
     *   .of(ThingErrorResponse.class, this::thingErrorResponseIsAsExpected)
     *   .run();
     * </pre>
     */
    public ExpectMsgAllClass expectMsgAllClass() {
        return new ExpectMsgAllClass();
    }

    /**
     * Same as {@link JavaTestKit#watch(ActorRef)}.
     */
    public ActorRef watch(final ActorRef actorRef) {
        return testProbe.watch(actorRef);
    }

    /**
     * Same as {@link JavaTestKit#unwatch(ActorRef)}.
     */
    public ActorRef unwatch(final ActorRef actorRef) {
        return testProbe.unwatch(actorRef);
    }

    /**
     * Same as {@link JavaTestKit#expectTerminated(ActorRef)}.
     *
     * @param target The actor expected to terminate.
     * @return The status of termination.
     */
    public Terminated expectTerminated(ActorRef target) {
        return testProbe.expectTerminated(target, Duration.Undefined());
    }

    /**
     * Same as {@link JavaTestKit#expectMsgEquals(Object)}.
     *
     * @param expectedMessage The expected message.
     * @return the actual message, which is equal to the expected message.
     * @throws AssertionError if the incoming message is not equal to the expected message.
     */
    public <T> T expectMsgEquals(final T expectedMessage) {
        return testProbe.expectMsg(expectedMessage);
    }

    /**
     * Pending assertions of {@link TestProbe#expectMsgAllClassOf(Seq)}
     */
    public final class ExpectMsgAllClass implements Runnable {

        private final List<ClassHandler> classHandlers = new ArrayList<>();

        public <S> ExpectMsgAllClass of(final Class<S> clazz, final Consumer<S> handler) {
            @SuppressWarnings("unchecked")
            final ClassHandler classHandler = new ClassHandler(clazz, handler);
            classHandlers.add(classHandler);
            return this;
        }

        @Override
        public void run() {
            final Class[] classes = classHandlers.stream().map(ClassHandler::getClazz).toArray(Class[]::new);
            final Seq<Object> messageSeq = testProbe.expectMsgAllClassOf(Util.immutableSeq(classes));
            final Object[] messages = toJavaArray(Object.class, messageSeq);
            for (int i = 0; i < classHandlers.size(); ++i) {
                classHandlers.get(i).handle(classes[i], messages[i]);
            }
        }
    }

    /**
     * Converts Scala Seq to Java array.
     *
     * @see akka.testkit.JavaTestKit#expectMsgAllOf(Object...)
     */
    private static <T> T[] toJavaArray(final Class<T> clazz, final Seq<T> seq) {
        return (T[]) seq.toArray(Util.classTag(clazz));
    }

    /**
     * Handler to be invoked when matched against a particular class, used internally.
     *
     * @param <T> The type of objects belong to that class.
     */
    private static final class ClassHandler<T> {

        private final Class<T> clazz;

        private final Consumer<T> handler;

        private ClassHandler(final Class<T> clazz, final Consumer<T> handler) {
            this.clazz = clazz;
            this.handler = handler;
        }

        private Class<T> getClazz() {
            return clazz;
        }

        /**
         * Matches against a class and executes the handler on match success.
         *
         * @param thatClazz The class to match against.
         * @param that A value of {@code thatClazz}.
         * @param <S> The type of {@code that}.
         * @return {@code true} if the classes match and the handler is executed, {@code false} otherwise.
         */
        private <S> boolean handle(final Class<S> thatClazz, final S that) {
            if (clazz.isInstance(that)) {
                @SuppressWarnings("unchecked")
                final T thatAsT = (T) that;
                handler.accept(thatAsT);
                return true;
            } else {
                return false;
            }
        }
    }
}
