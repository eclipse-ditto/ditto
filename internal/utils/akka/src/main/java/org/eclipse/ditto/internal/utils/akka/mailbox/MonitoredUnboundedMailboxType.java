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

 import java.util.Optional;
 import java.util.Queue;
 import java.util.concurrent.ConcurrentLinkedQueue;
 import java.util.concurrent.atomic.AtomicInteger;
 import java.util.regex.Pattern;

 import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
 import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import com.typesafe.config.Config;

 import akka.actor.ActorPath;
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

 /**
  * MonitoredUnboundedMailboxType is a regular unbounded mailbox, which in addition monitors the size of the
  * mailbox/queue.
  * It is backed by a {@link ConcurrentLinkedQueue}, like the default {@link UnboundedMailbox} of Akka. Therefore it can be
  * used to replace the default unbounded mailbox.
  * <p>
  * This mailbox has two additional monitoring features: Logging the size of the mailbox when a given
  * threshold is reached and a mailbox size gauge metric.
  */
 public final class MonitoredUnboundedMailboxType
         implements MailboxType, ProducesMessageQueue<UnboundedMailbox.MessageQueue> {

     private static final Logger LOGGER = LoggerFactory.getLogger(MonitoredUnboundedMailboxType.class);
     private static final Gauge MAILBOX_SIZE = DittoMetrics.gauge("actor_mailbox_size");
     private static final String CONFIG_OBJECT_PATH = "monitored-unbounded-mailbox";
     private static final String THRESHOLD_FOR_LOGGING_PATH = "threshold-for-logging";
     private static final String LOGGING_INTERVAL_PATH = "logging-interval";
     private static final String ACTORS_INCLUDE_REGEX_PATH = "include-actors-regex";
     private static final String ACTORS_EXCLUDE_REGEX_PATH = "exclude-actors-regex";

     private final Config mailboxConfig;
     private final Pattern includeRegexFilters;
     private final Pattern excludeRegexFilters;

     /**
      * Creates a new {@code MonitoredUnboundedMailboxType}.
      * This constructor signature must exist, it will be called by Akka.
      *
      * @param settings the ActorSystem settings.
      * @param config the config.
      */
     public MonitoredUnboundedMailboxType(final ActorSystem.Settings settings, final Config config) {
         this.mailboxConfig = config.getConfig(CONFIG_OBJECT_PATH);
         this.includeRegexFilters = Pattern.compile(mailboxConfig.getString(ACTORS_INCLUDE_REGEX_PATH));
         this.excludeRegexFilters = Pattern.compile(mailboxConfig.getString(ACTORS_EXCLUDE_REGEX_PATH));
     }

     /**
      * Factory for the actual mailbox/queue. Depending on the configured include/exclude actor filters, it either
      * creates a normal message queue or an instrumented queue, which keeps track of its size.
      *
      * @param owner the owner actor of this mailbox.
      * @param system the actor system this mailbox is part of.
      * @return a queue
      */
     @Override
     public MessageQueue create(final Option<ActorRef> owner, final Option<ActorSystem> system) {
         if (owner.nonEmpty() && system.nonEmpty()) {
             final ActorRef mailboxOwner = owner.get();

             if (shouldTrackActor(mailboxOwner.path())) {
                 final int threshold = mailboxConfig.getInt(THRESHOLD_FOR_LOGGING_PATH);
                 final long interval = mailboxConfig.getLong(LOGGING_INTERVAL_PATH);
                 return new InstrumentedMessageQueue(mailboxOwner, system.get(), threshold, interval);
             }
         } else {
             LOGGER.warn("Mailbox creation not possible, owner actor <{}> or system <{}> not available", owner, system);
         }
         // Use akka default unbounded mailbox as fallback
         return new UnboundedMailbox.MessageQueue();
     }


     /**
      * Decides whether mailbox size of the actor shall be tracked or not.
      *
      * @param path the path of the actor.
      * @return the made decision.
      */
     private boolean shouldTrackActor(final ActorPath path) {
         final String pathWithoutAddress = path.toStringWithoutAddress();
         if (excludeRegexFilters.matcher(pathWithoutAddress).matches()) {
             return false;
         } else {
             return includeRegexFilters.matcher(pathWithoutAddress).matches();
         }
     }

     /**
      * A {@code MessageQueue} implementation which keeps track of its size. The size is tracked separately, to avoid
      * {@link Queue#size} complexity of O(n). The size will be reported via logging and a gauge metric.
      */
     static final class InstrumentedMessageQueue implements MessageQueue, akka.dispatch.UnboundedMessageQueueSemantics {

         private final LoggingAdapter log;
         private final ActorPath path;
         private final int threshold;
         private final long interval;
         private final String ownerActorClassName;
         private final Gauge mailboxSizeByActorClassGauge;
         private final Queue<Envelope> queue = new ConcurrentLinkedQueue<>();
         private final AtomicInteger queueSize = new AtomicInteger();
         private volatile long lastLogTime = System.nanoTime();

         InstrumentedMessageQueue(final ActorRef owner, final ActorSystem system, final int threshold,
                 final long interval) {
             this.log = Logging.getLogger(system, InstrumentedMessageQueue.class);
             this.path = owner.path();
             this.threshold = threshold;
             this.interval = interval;
             this.ownerActorClassName = getClassOfOwnerActorRef(owner).orElse("unknown-actor-class");
             this.mailboxSizeByActorClassGauge = MAILBOX_SIZE.tag("actor-class", ownerActorClassName);
             log.debug("instrumented queue created for actor {} of class {}", path, ownerActorClassName);
         }

         /**
          * Uses internal akka API to retrieve the Class of the owner Actor.
          *
          * @param owner actor reference of the owner.
          * @return the name of the actor class or empty.
          */
         private static Optional<String> getClassOfOwnerActorRef(final ActorRef owner) {
             if (owner instanceof ActorRefWithCell actorRefWithCell) {
                 return Optional.ofNullable(actorRefWithCell.underlying().props().actorClass().getCanonicalName());
             } else {
                 return Optional.empty();
             }
         }

         @Override
         public Envelope dequeue() {
             Envelope handle = queue.poll();
             if (handle != null) {
                 mailboxSizeByActorClassGauge.decrement();
                 int size = queueSize.decrementAndGet();
                 logMailboxSize(size);
             }
             return handle;
         }

         @Override
         public void enqueue(final ActorRef receiver, final Envelope handle) {
             queue.offer(handle);
             int mailboxSize = queueSize.incrementAndGet();
             mailboxSizeByActorClassGauge.increment();
             logMailboxSize(mailboxSize);
         }

         private void logMailboxSize(final int mailboxSize) {
             if (mailboxSize >= threshold) {
                 long now = System.nanoTime();
                 if (now - lastLogTime > interval) {
                     lastLogTime = now;
                     log.info("Mailbox size is <{}> for <{}> of class <{}>", mailboxSize, path, ownerActorClassName);
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
         public void cleanUp(final ActorRef owner, final MessageQueue deadLetters) {
             for (Envelope handle : queue) {
                 deadLetters.enqueue(owner, handle);
                 mailboxSizeByActorClassGauge.decrement();
                 queueSize.decrementAndGet();
             }
         }
     }
 }
