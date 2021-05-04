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
package org.eclipse.ditto.base.model.signals;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Manage creation and termination of actor systems for tests.
 */
public final class DittoTestSystem implements AutoCloseable {

    private final ActorSystem actorSystem;

    private DittoTestSystem(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Run a unit test in its own actor system and test kit.
     *
     * @param test the JUnit test object.
     * @param assertions the assertions to run.
     */
    public static void run(final Object test, final TestConsumer assertions) {
        final String actorSystemName = test.getClass().getSimpleName();
        try (final DittoTestSystem testSystem = new DittoTestSystem(ActorSystem.create(actorSystemName))) {
            new TestKit(testSystem.actorSystem) {{
                assertions.accept(this);
            }};
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void close() throws Exception {
        TestKit.shutdownActorSystem(actorSystem);
    }

    /**
     * Interface for test code.
     */
    @FunctionalInterface
    public interface TestConsumer {

        /**
         * Consume an argument with the potential to throw any exception.
         *
         * @param testKit the test kit.
         * @throws Exception if anything went wrong.
         */
        void accept(final TestKit testKit) throws Exception;
    }
}
