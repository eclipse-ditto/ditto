 /*
  * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.internal.utils.akka.mailbox;

 import static akka.Done.done;
 import static org.assertj.core.api.Assertions.assertThat;

 import java.util.concurrent.CountDownLatch;
 import java.util.concurrent.TimeUnit;
 import java.util.stream.IntStream;

 import org.junit.AfterClass;
 import org.junit.BeforeClass;
 import org.junit.Test;

 import com.typesafe.config.Config;
 import com.typesafe.config.ConfigFactory;

 import akka.actor.AbstractActor;
 import akka.actor.ActorRef;
 import akka.actor.ActorSystem;
 import akka.actor.Props;
 import akka.dispatch.MessageQueue;
 import akka.dispatch.UnboundedMailbox;
 import akka.event.Logging;
 import akka.event.LoggingAdapter;
 import akka.testkit.javadsl.EventFilter;
 import akka.testkit.javadsl.TestKit;
 import scala.Option;

 public final class MonitoredUnboundedMailboxTypeTest {

     private final static String ACTOR_SYSTEM_NAME = "monitored-unbounded-mailbox-test";
     private final static String TEST_MAILBOX_NAME = "test-monitored-unbounded-mailbox";
     private static Config testConfig;
     private static ActorSystem system;


     @BeforeClass
     public static void init() {
         testConfig = ConfigFactory.load("monitored-mailbox-test");
         system = ActorSystem.create(ACTOR_SYSTEM_NAME, testConfig);
         final LoggingAdapter logger = Logging.getLogger(system, MonitoredUnboundedMailboxTypeTest.class);


     }

     @AfterClass
     public static void cleanup() {
         if (system != null) {
             TestKit.shutdownActorSystem(system);
         }
     }

     @Test
     public void testMonitoredUnboundedMailboxShouldLogWhenThresholdIsReached() {
         new TestKit(system) {
             {
                 // Arrange
                 final CountDownLatch sleepUntilLatch = new CountDownLatch(1);
                 final int thresholdForLogging =
                         testConfig.getInt(TEST_MAILBOX_NAME + ".monitored-unbounded-mailbox.threshold-for-logging");

                 final Props props = Props.create(AbstractActor.class, () -> new AbstractActor() {
                     @Override
                     public Receive createReceive() {
                         return receiveBuilder()
                                 .matchEquals("sleep",
                                         s -> {
                                             getSender().tell("sleepy", getSelf());
                                             sleepUntilLatch.await(10, TimeUnit.SECONDS);
                                         })
                                 .match(Integer.class, s -> getSender().tell(s, getSelf()))
                                 .build();
                     }
                 });
                 final ActorRef subject = system.actorOf(props.withMailbox(TEST_MAILBOX_NAME),
                         "sleepy-test-actor");

                 // Act, Assert
                 new EventFilter(Logging.Info.class, system)
                         .startsWith("Mailbox size is <" + thresholdForLogging)
                         .occurrences(1)
                         .intercept(() -> {
                             subject.tell("sleep", getRef());
                             expectMsg("sleepy");
                             IntStream.range(0, thresholdForLogging).forEach(val -> subject.tell(val, getRef()));
                             return done();
                         });

                 // clean-up, free actor
                 sleepUntilLatch.countDown();
             }
         };
     }

     @Test
     public void testActorShouldNotBeTrackedByMonitoredUnboundedMailbox() {
         final Props withMailbox = Props.empty().withMailbox(TEST_MAILBOX_NAME);
         final ActorRef actorRef = system.actorOf(withMailbox, "excluded-actor");

         final MonitoredUnboundedMailboxType monitoredUnboundedMailboxType =
                 new MonitoredUnboundedMailboxType(system.settings(),
                         testConfig.getConfig(TEST_MAILBOX_NAME));
         final MessageQueue messageQueue =
                 monitoredUnboundedMailboxType.create(Option.apply(actorRef), Option.apply(system));

         assertThat(messageQueue).isInstanceOf(UnboundedMailbox.MessageQueue.class);
     }

 }
