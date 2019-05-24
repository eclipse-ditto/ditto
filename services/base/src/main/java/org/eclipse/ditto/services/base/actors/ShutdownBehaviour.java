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
 package org.eclipse.ditto.services.base.actors;

 import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
 import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

 import org.eclipse.ditto.model.namespaces.NamespaceReader;
 import org.eclipse.ditto.signals.commands.common.Shutdown;
 import org.eclipse.ditto.signals.commands.common.ShutdownReason;

 import akka.actor.ActorRef;
 import akka.actor.PoisonPill;
 import akka.cluster.pubsub.DistributedPubSubMediator;
 import akka.japi.pf.ReceiveBuilder;

 /**
  * Responsible for shutting down the given actor in case a shutdown command contains a reason that is applicable for
  * the information hold by this behaviour.
  */
 public final class ShutdownBehaviour {

     private final String namespace;
     private final String entityId;

     private final ActorRef self;

     private ShutdownBehaviour(final String namespace, final String entityId, final ActorRef self) {
         this.namespace = namespace;
         this.entityId = entityId;
         this.self = self;
     }

     /**
      * Create the actor behavior from its entity ID and reference.
      *
      * @param entityId entity ID to react to.
      * @param pubSubMediator Akka pub-sub mediator.
      * @param self reference of the actor itself.
      * @return the actor behavior.
      */
     public static ShutdownBehaviour fromId(final String entityId, final ActorRef pubSubMediator,
             final ActorRef self) {

         argumentNotEmpty(entityId, "Entity ID");
         checkNotNull(self, "Self");

         final String namespace = NamespaceReader.fromEntityId(entityId).orElse("");

         final ShutdownBehaviour purgeEntitiesBehaviour = new ShutdownBehaviour(namespace, entityId, self);

         purgeEntitiesBehaviour.subscribePubSub(checkNotNull(pubSubMediator, "Pub-Sub-Mediator"));
         return purgeEntitiesBehaviour;
     }

     private void subscribePubSub(final ActorRef pubSubMediator) {
         pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(Shutdown.TYPE, self), self);
     }

     /**
      * Create a new receive builder matching on messages handled by this actor.
      *
      * @return new receive builder.
      */
     public ReceiveBuilder createReceive() {
         return ReceiveBuilder.create()
                 .match(Shutdown.class, this::shutdown)
                 .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck);
     }

     private void shutdown(final Shutdown shutdown) {
         final ShutdownReason shutdownReason = shutdown.getReason();

         if(shutdownReason.isRelevantFor(namespace) || shutdownReason.isRelevantFor(entityId)) {
             self.tell(PoisonPill.getInstance(), ActorRef.noSender());
         }
     }

     private void subscribeAck(final DistributedPubSubMediator.SubscribeAck ack) {
         // do nothing
     }
 }
