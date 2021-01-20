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
 package org.eclipse.ditto.services.utils.akka.mailbox;

 import java.util.Optional;
 import java.util.Queue;
 import java.util.concurrent.ConcurrentLinkedQueue;
 import java.util.concurrent.atomic.AtomicInteger;

 import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
 import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;

 import com.typesafe.config.Config;
 import com.typesafe.config.ConfigObject;

 import akka.actor.ActorRef;
 import akka.actor.ActorRefWithCell;
 import akka.actor.ActorSystem;
 import akka.dispatch.Envelope;
 import akka.dispatch.MailboxType;
 import akka.dispatch.MessageQueue;
 import akka.dispatch.ProducesMessageQueue;
 import akka.dispatch.UnboundedMailbox;
 import akka.event.Logging;
 import akka.event.LoggingAdapter;
 import scala.Option;

 public final class LoggingMailboxType implements MailboxType, ProducesMessageQueue<UnboundedMailbox.MessageQueue> {

     private static final Gauge MAILBOX_SIZE = DittoMetrics.gauge("actor-mailbox-size");

     private final Config config;

     public LoggingMailboxType(final ActorSystem.Settings settings, final Config config) {
         this.config = config;
     }

     @Override
     public MessageQueue create(final Option<ActorRef> owner, final Option<ActorSystem> system) {
         if (owner.isEmpty() || system.isEmpty()) {
             throw new IllegalArgumentException("no mailbox owner or system given");
         }

         final ActorRef mailboxOwner = owner.get();

         final Config mailboxConfig = config.getObject("logging-mailbox").toConfig();

         final int threshold = mailboxConfig.getInt("threshold-for-logging");
         final long interval = mailboxConfig.getLong("logging-interval");

         if (mailboxOwner.path().toStringWithoutAddress().startsWith("/user")) {
             return new LoggingMailbox(mailboxOwner, system.get(), threshold, interval);
         } else {
             return new UnboundedMailbox.MessageQueue();
         }
     }

     static final class LoggingMailbox implements MessageQueue, akka.dispatch.UnboundedMessageQueueSemantics {

         private final Queue<Envelope> queue = new ConcurrentLinkedQueue<>();
         private final int threshold;
         private final LoggingAdapter log;
         private final long interval;
         private final String ownerActorClassName;
         private final String path;
         private final AtomicInteger queueSize = new AtomicInteger();
         private final AtomicInteger dequeueCount = new AtomicInteger();
         private final Gauge mailboxSizeByActorClassGauge;
         volatile private long logTime = System.nanoTime();

         LoggingMailbox(final ActorRef owner, final ActorSystem system, final int threshold, final long interval) {

             this.path = owner.path().toString();
             this.threshold = threshold;
             this.interval = interval;
             this.log = Logging.getLogger(system, LoggingMailbox.class);
             this.ownerActorClassName = getClassOfOwnerActorRef(owner).orElse("unknown-actor-class");
             this.mailboxSizeByActorClassGauge = MAILBOX_SIZE.tag("actor-class", ownerActorClassName);
         }

         @Override
         public Envelope dequeue() {
             Envelope x = queue.poll();
             if (x != null) {
                 mailboxSizeByActorClassGauge.decrement();
                 int size = queueSize.decrementAndGet();
                 dequeueCount.incrementAndGet();
                 logSize(size);
             }
             return x;
         }

         @Override
         public void enqueue(ActorRef receiver, Envelope handle) {
             queue.offer(handle);
             int size = queueSize.incrementAndGet();
             mailboxSizeByActorClassGauge.increment();
             logSize(size);
         }

         private void logSize(int size) {
             if (size >= threshold) {
                 long now = System.nanoTime();
                 if (now - logTime > interval) {
                     double msgPerSecond = ((double) dequeueCount.get()) / (((double) (now - logTime)) / 1000000000L);
                     logTime = now;
                     dequeueCount.set(0);
                     log.info("Mailbox size for <{}> is <{}>, processing <{}> msg/s", path, size,
                             String.format("%2.2f", msgPerSecond));
                 }
             }
         }

         @Override
         public int numberOfMessages() {
             return queueSize.get();
         }

         @Override
         public boolean hasMessages() {
             return !queue.isEmpty();
         }

         @Override
         public void cleanUp(ActorRef owner, MessageQueue deadLetters) {
             for (Envelope handle : queue) {
                 deadLetters.enqueue(owner, handle);
             }
         }
     }

     private static Optional<String> getClassOfOwnerActorRef(ActorRef owner) {
         if (owner instanceof ActorRefWithCell) {
             final ActorRefWithCell ownerWithCell = (ActorRefWithCell) owner;
             return Optional.ofNullable(ownerWithCell.underlying().props().actorClass().getCanonicalName());
         } else {
             return Optional.empty();
         }
     }
 }
