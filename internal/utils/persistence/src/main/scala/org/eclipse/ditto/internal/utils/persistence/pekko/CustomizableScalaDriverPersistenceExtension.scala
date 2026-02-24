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
import com.mongodb.connection.TransportSettings
import com.typesafe.config.Config
import io.netty.handler.ssl.SslContextBuilder
import org.apache.pekko.actor.ActorSystem
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig
import org.eclipse.ditto.internal.utils.persistence.mongo.auth.AwsAuthenticationHelper
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig
import pekko.contrib.persistence.mongodb.driver.{ScalaDriverPersistenceJournaller, ScalaDriverPersistenceReadJournaller, ScalaDriverPersistenceSnapshotter, ScalaMongoDriver}
import pekko.contrib.persistence.mongodb.{ConfiguredExtension, MongoPersistenceExtension, MongoPersistenceJournalMetrics, MongoPersistenceJournallingApi}

import java.io.File
import javax.net.ssl.SSLException

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

      val mongoCredential = if (optionsConfig.isUseAwsIamRole) {
        AwsAuthenticationHelper.provideAwsIamBasedMongoCredential(
          optionsConfig.awsRegion(),
          optionsConfig.awsRoleArn(),
          optionsConfig.awsSessionName()
        )
      } else if (optionsConfig.isUseX509Authentication) {
        MongoCredential.createMongoX509Credential()
      } else {
        null
      }

      new CustomizableScalaMongoDriver(actorSystem, config, builder => {
        if (mongoCredential != null) {
          builder.credential(mongoCredential)
        }

        builder.applyToSslSettings(sslBuilder => sslBuilder.enabled(optionsConfig.isSslEnabled))
          .transportSettings(TransportSettings.nettyBuilder()
            .sslContext(tryToCreateAndInitSslContext(optionsConfig.sslCaFile(), optionsConfig.sslClientCertFile(),
                optionsConfig.sslClientKeyFile(), optionsConfig.sslClientKeyPassword()))
            .build())

        builder
      })
    }

    override lazy val journaler: MongoPersistenceJournallingApi = new ScalaDriverPersistenceJournaller(driver)
      with MongoPersistenceJournalMetrics {
      override def driverName = "scala-official"
    }
    override lazy val snapshotter = new ScalaDriverPersistenceSnapshotter(driver)

    override lazy val readJournal = new ScalaDriverPersistenceReadJournaller(driver)

    private def tryToCreateAndInitSslContext(sslCa: String, sslClientCert: String, sslClientKey: String, sslClientKeyPassword: String)
    = try createAndInitSslContext(sslCa, sslClientCert, sslClientKey, sslClientKeyPassword)
    catch {
      case e: SSLException =>
        throw new IllegalArgumentException("SSLException!", e)
    }

    @throws[SSLException]
    private def createAndInitSslContext(sslCa: String, sslClientCert: String, sslClientKey: String, sslClientKeyPassword: String) = {
      val builder = SslContextBuilder.forClient
      if (sslCa != null && sslCa.nonEmpty) {
        val sslCaFile = new File(sslCa)
        builder.trustManager(sslCaFile)
      }

      if (sslClientCert != null && sslClientCert.nonEmpty) {
        val sslClientCertFile = new File(sslClientCert)
        val sslClientKeyFile = new File(sslClientKey)
        builder.keyManager(sslClientCertFile, sslClientKeyFile,
          if (sslClientKeyPassword.isEmpty) null else sslClientKeyPassword)
      }

      builder.build
    }
  }

}
