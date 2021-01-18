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
 package org.eclipse.ditto.services.utils.akka.actors.mailbox;

 import java.util.Queue;
 import java.util.concurrent.ConcurrentLinkedQueue;
 import java.util.concurrent.atomic.AtomicInteger;

 import com.typesafe.config.Config;

 import akka.actor.ActorRef;
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
  * Logs the mailbox size when exceeding the configured limit. It logs at most
  * once per second when the messages are enqueued or dequeued.
  *
  * Configuration:
  *
  * <pre>
  * akka.actor.default-mailbox {
  *   mailbox-type = akka.contrib.mailbox.LoggingMailboxType
  *   size-limit = 20
  * }
  * </pre>
  */
 public class LoggingMailboxType implements MailboxType, ProducesMessageQueue<UnboundedMailbox.MessageQueue> {
     private final Config config;

     public LoggingMailboxType(ActorSystem.Settings settings, Config config) {
         this.config = config;
     }

     @Override
     public MessageQueue create(Option<ActorRef> owner, Option<ActorSystem> system) {
         if (owner.isEmpty() || system.isEmpty())
             throw new IllegalArgumentException("no mailbox owner or system given");
         int sizeLimit = config.getInt("size-limit");
         return new LoggingMailbox(owner.get(), system.get(), sizeLimit);
     }

     static class LoggingMailbox implements MessageQueue, akka.dispatch.UnboundedMessageQueueSemantics {

         private final Queue<Envelope> queue = new ConcurrentLinkedQueue<Envelope>();

         private final int sizeLimit;
         private final LoggingAdapter log;
         private final long interval = 1000000000L; // 1 s, in nanoseconds
         private final String path;
         volatile private long logTime = System.nanoTime();
         private final AtomicInteger queueSize = new AtomicInteger();
         private final AtomicInteger dequeueCount = new AtomicInteger();

         LoggingMailbox(ActorRef owner, ActorSystem system, int sizeLimit) {
             this.path = owner.path().toString();
             this.sizeLimit = sizeLimit;
             this.log = Logging.getLogger(system, LoggingMailbox.class);
         }

         @Override
         public Envelope dequeue() {
             Envelope x = queue.poll();
             if (x != null) {
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
             logSize(size);
         }

         private void logSize(int size) {
             if (size >= sizeLimit) {
                 long now = System.nanoTime();
                 if (now - logTime > interval) {
                     double msgPerSecond = ((double) dequeueCount.get()) / (((double) (now - logTime)) / 1000000000L);
                     logTime = now;
                     dequeueCount.set(0);
                     log.info("Mailbox size for [{}] is [{}], processing [{}] msg/s", path, size,
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
 }
