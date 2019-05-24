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
 package org.eclipse.ditto.services.utils.persistence.mongo.ops;

 import java.time.Duration;
 import java.util.Optional;

 import com.typesafe.config.Config;

 public final class PersistenceOperationsConfiguration {

     private static final String PREFIX = "ditto.persistence.operations.";

     private static final String DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN_KEY = "delay-after-persistence-actor-shutdown";
     private static final Duration DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN_DEFAULT = Duration.ofSeconds(5);

     private final Duration delayAfterPersistenceActorShutdown;

     private PersistenceOperationsConfiguration(final Duration delayAfterPersistenceActorShutdown) {
         this.delayAfterPersistenceActorShutdown = delayAfterPersistenceActorShutdown;
     }

     public static PersistenceOperationsConfiguration fromConfig(final Config config) {

         final Duration delayAfterPersistenceActorShutdown =
                 Optional.ofNullable(config.getDuration(PREFIX + DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN_KEY))
                         .orElse(DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN_DEFAULT);

         return new PersistenceOperationsConfiguration(delayAfterPersistenceActorShutdown);
     }

     Duration getDelayAfterPersistenceActorShutdown() {
         return delayAfterPersistenceActorShutdown;
     }
 }
