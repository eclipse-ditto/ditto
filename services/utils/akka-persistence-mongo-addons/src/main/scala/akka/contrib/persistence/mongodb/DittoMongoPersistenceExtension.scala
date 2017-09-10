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

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorSystem, ExtendedActorSystem, ExtensionId}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object DittoMongoPersistenceExtension extends ExtensionId[DittoMongoPersistenceExtension] {

  def lookup = DittoMongoPersistenceExtension

  override def createExtension(actorSystem: ExtendedActorSystem) = {
    val settings = MongoSettings(actorSystem.settings)
    val implementation = settings.Implementation
    val implType = Class.forName(implementation)
    val implCons = implType.getConstructor(classOf[ActorSystem])
    implCons.newInstance(actorSystem).asInstanceOf[DittoMongoPersistenceExtension]
  }

  override def get(actorSystem: ActorSystem) = super.get(actorSystem)
}

/**
  * Trait extending the [[MongoPersistenceExtension]] provided by "akka-persistence-mongo" plugin.
  * Required as we need to return [[DittoConfiguredExtension]] in "configured" Method.
  */
trait DittoMongoPersistenceExtension extends MongoPersistenceExtension {

  private val configuredExtensions = new ConcurrentHashMap[Config, DittoConfiguredExtension].asScala

  override def apply(config: Config): DittoConfiguredExtension = {
    configuredExtensions.putIfAbsent(config, configured(config))
    configuredExtensions.get(config).get
  }

  def configured(config: Config): DittoConfiguredExtension

}

/**
  * Trait extending the [[ConfiguredExtension]] provided by "akka-persistence-mongo" plugin.
  * Required as we need to override the "readJournal" to use.
  */
trait DittoConfiguredExtension extends ConfiguredExtension {
  override def readJournal: DittoMongoPersistenceReadJournallingApi
}
