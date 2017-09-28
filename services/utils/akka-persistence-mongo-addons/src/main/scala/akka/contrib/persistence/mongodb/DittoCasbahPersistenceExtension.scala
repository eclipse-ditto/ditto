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
package akka.contrib.persistence.mongodb

import akka.actor.ActorSystem
import com.typesafe.config.Config

/**
  * Class proving the "driver" for the specialized Ditto [[MongoPersistenceExtension]] extended by the
  * "akka-persistence-mongo" plugin.
  *
  * Use in config like this:
  * {{{
  * akka.contrib.persistence.mongodb.mongo.driver = "akka.contrib.persistence.mongodb.DittoCasbahPersistenceExtension"
  * }}}
  *
  * @param actorSystem the Akka system.
  */
class DittoCasbahPersistenceExtension(val actorSystem: ActorSystem) extends DittoMongoPersistenceExtension {

  override def configured(config: Config): Configured = Configured(config)

  case class Configured(config: Config) extends DittoConfiguredExtension {

    override lazy val journaler = new CasbahPersistenceJournaller(driver) with MongoPersistenceJournalMetrics with MongoPersistenceJournalFailFast {
      override def driverName = "casbah"

      override private[mongodb] val breaker = driver.breaker
    }
    override lazy val snapshotter = new CasbahPersistenceSnapshotter(driver) with MongoPersistenceSnapshotFailFast {
      override private[mongodb] val breaker = driver.breaker
    }
    override lazy val readJournal = new DittoCasbahPersistenceReadJournaller(driver)
    val driver = {
      val theDriver = new CasbahMongoDriver(actorSystem, config)
      // log the driver options
      actorSystem.log.info("DittoCasbahPersistenceExtension created MongoDriver with options <{}>", theDriver.client.underlying.getMongoClientOptions)
      theDriver
    }
  }

}
