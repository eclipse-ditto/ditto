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

import com.mongodb.MongoCredential
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig
import org.eclipse.ditto.internal.utils.persistence.mongo.auth.AwsAuthenticationHelper
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig
import pekko.contrib.persistence.mongodb.driver.{ScalaDriverPersistenceJournaller, ScalaDriverPersistenceReadJournaller, ScalaDriverPersistenceSnapshotter, ScalaMongoDriver}
import pekko.contrib.persistence.mongodb.{ConfiguredExtension, MongoPersistenceExtension, MongoPersistenceJournalMetrics, MongoPersistenceJournallingApi}

/**
 * An adjustment of the original pekko-persistence
 * [[pekko.contrib.persistence.mongodb.driver.ScalaDriverPersistenceExtension]] which can be customized in a way to
 * overwrite configuration of the used [[ScalaMongoDriver]].
 * Creates an instance of [[CustomizableScalaMongoDriver]] when a custom [[MongoCredential]] should be provided
 * to the driver in order to authenticate.
 *
 * @param actorSystem the ActorSystem in which the extension was loaded
 */
class CustomizableScalaDriverPersistenceExtension(val actorSystem: ActorSystem)
  extends MongoPersistenceExtension(actorSystem) {

  override def configured(config: Config): Configured = Configured(config)

  case class Configured(config: Config) extends ConfiguredExtension {

    val driver: ScalaMongoDriver = {
      val mongoDbConfig = DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings.config))
      val optionsConfig = mongoDbConfig.getOptionsConfig

      if (optionsConfig.isUseAwsIamRole) {
        val mongoCredential = AwsAuthenticationHelper.provideAwsIamBasedMongoCredential(
          optionsConfig.awsRegion(),
          optionsConfig.awsRoleArn(),
          optionsConfig.awsSessionName()
        )
        new CustomizableScalaMongoDriver(actorSystem, config, builder => builder.credential(mongoCredential))
      } else {
        new ScalaMongoDriver(actorSystem, config)
      }
    }

    override lazy val journaler: MongoPersistenceJournallingApi = new ScalaDriverPersistenceJournaller(driver)
      with MongoPersistenceJournalMetrics {
      override def driverName = "scala-official"
    }
    override lazy val snapshotter = new ScalaDriverPersistenceSnapshotter(driver)

    override lazy val readJournal = new ScalaDriverPersistenceReadJournaller(driver)
  }

}
