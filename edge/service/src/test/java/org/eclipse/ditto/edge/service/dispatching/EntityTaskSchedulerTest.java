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
package org.eclipse.ditto.edge.service.dispatching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.things.model.ThingId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link EntityTaskScheduler}.
 */
public final class EntityTaskSchedulerTest {

    public static final ThingId THING_ID = ThingId.of("foo:bar");
    public static final ThingId THING_ID_2 = ThingId.of("foo:bar2");
    public static final ThingId THING_ID_3 = ThingId.of("foo:bar3");
    @Nullable private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void ensureOrderForSameEntityId() {
        assert actorSystem != null;
        new TestKit(actorSystem) {{
            final Props props = EntityTaskScheduler.props("test");
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<>().completeOnTimeout(1, 50, TimeUnit.MILLISECONDS)), getRef());
            underTest.tell(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<>().completeOnTimeout(2, 0, TimeUnit.MILLISECONDS)), getRef());
            underTest.tell(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<>().completeOnTimeout(3, 10, TimeUnit.MILLISECONDS)), getRef());

            expectMsg(new EntityTaskScheduler.TaskResult<>(1, null));
            expectMsg(new EntityTaskScheduler.TaskResult<>(2, null));
            expectMsg(new EntityTaskScheduler.TaskResult<>(3, null));
        }};
    }

    @Test
    public void ensureMessagesForDifferentEntityIdsAreProcessedInParallelWhileEnsuringOrder() {
        assert actorSystem != null;
        new TestKit(actorSystem) {{
            final Props props = EntityTaskScheduler.props("test");
            final ActorRef underTest = actorSystem.actorOf(props);

            final List<EntityTaskScheduler.Task<Double>> oneTasks = new ArrayList<>();
            oneTasks.add(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<Double>().completeOnTimeout(1.1, 20, TimeUnit.MILLISECONDS)));
            oneTasks.add(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<Double>().completeOnTimeout(1.2, 0, TimeUnit.MILLISECONDS)));
            oneTasks.add(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<Double>().completeOnTimeout(1.3, 50, TimeUnit.MILLISECONDS)));
            oneTasks.add(new EntityTaskScheduler.Task<>(THING_ID,
                    () -> new CompletableFuture<Double>().completeOnTimeout(1.4, 10, TimeUnit.MILLISECONDS)));

            final List<EntityTaskScheduler.Task<Double>> twoTasks = new ArrayList<>();
            twoTasks.add(new EntityTaskScheduler.Task<>(THING_ID_2,
                    () -> new CompletableFuture<Double>().completeOnTimeout(2.1, 0, TimeUnit.MILLISECONDS)));
            twoTasks.add(new EntityTaskScheduler.Task<>(THING_ID_2,
                    () -> new CompletableFuture<Double>().completeOnTimeout(2.2, 0, TimeUnit.MILLISECONDS)));
            twoTasks.add(new EntityTaskScheduler.Task<>(THING_ID_2,
                    () -> new CompletableFuture<Double>().completeOnTimeout(2.3, 0, TimeUnit.MILLISECONDS)));

            final List<EntityTaskScheduler.Task<Double>> threeTasks = new ArrayList<>();
            threeTasks.add(new EntityTaskScheduler.Task<>(THING_ID_3,
                    () -> new CompletableFuture<Double>().completeOnTimeout(3.1, 50, TimeUnit.MILLISECONDS)));
            threeTasks.add(new EntityTaskScheduler.Task<>(THING_ID_3,
                    () -> new CompletableFuture<Double>().completeOnTimeout(3.2, 0, TimeUnit.MILLISECONDS)));
            threeTasks.add(new EntityTaskScheduler.Task<>(THING_ID_3,
                    () -> new CompletableFuture<Double>().completeOnTimeout(3.3, 20, TimeUnit.MILLISECONDS)));

            final List<EntityTaskScheduler.Task<Double>> allTasks = new ArrayList<>();
            allTasks.addAll(oneTasks);
            allTasks.addAll(twoTasks);
            allTasks.addAll(threeTasks);
            allTasks.forEach(task -> underTest.tell(task, getRef()));

            final List<Double> oneResults = new ArrayList<>();
            final List<Double> twoResults = new ArrayList<>();
            final List<Double> threeResults = new ArrayList<>();

            allTasks.forEach(task -> {
                    final EntityTaskScheduler.TaskResult<Double> taskResult =
                            expectMsgClass(EntityTaskScheduler.TaskResult.class);
                    final Double result = taskResult.result();
                    assertThat(result).isNotNull();
                    switch (result.intValue()) {
                        case 1 -> oneResults.add(result);
                        case 2 -> {
                            twoResults.add(result);
                            if (twoResults.size() == twoTasks.size()) {
                                // twos are finished - ensure that they are first and the others still wait for results:
                                assertThat(oneResults)
                                        .hasSizeLessThan(oneTasks.size());
                                assertThat(threeResults)
                                        .hasSizeLessThan(threeTasks.size());
                            }
                        }
                        case 3 -> threeResults.add(result);
                        default -> throw new AssertionError("Not expected result");
                    }
            });

            assertThat(oneResults).isSorted();
            assertThat(twoResults).isSorted();
            assertThat(threeResults).isSorted();
        }};
    }

}
