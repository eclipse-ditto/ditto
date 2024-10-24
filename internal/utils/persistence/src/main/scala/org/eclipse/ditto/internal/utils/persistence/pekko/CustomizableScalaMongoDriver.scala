/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.pekko

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.MongoClientSettings
import org.mongodb.scala.MongoClientSettings.Builder
import pekko.contrib.persistence.mongodb.driver.ScalaMongoDriver

/**
 * A customizable [[ScalaMongoDriver]] in which the MongoDB driver's [[MongoClientSettings]] can be adjusted via a
 * provided callback for a [[MongoClientSettings.Builder]].
 *
 * @param system the ActorSystem
 * @param config the config to apply
 * @param clientSettingsBuilder a callback, providing the [[MongoClientSettings.Builder]] after pekko-persistence
 *                              configuration was applied with the purpose to customize it prior to building the
 *                              [[org.mongodb.scala.MongoClient]]
 */
class CustomizableScalaMongoDriver(system: ActorSystem, config: Config,
                                   clientSettingsBuilder: Builder => Builder)
  extends ScalaMongoDriver(system, config) {

  override val mongoClientSettings: MongoClientSettings = clientSettingsBuilder
    .apply(scalaDriverSettings.configure(mongoUri))
    .build()

}
