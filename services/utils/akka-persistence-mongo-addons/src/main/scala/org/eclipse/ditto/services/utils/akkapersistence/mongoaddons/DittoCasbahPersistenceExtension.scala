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
package org.eclipse.ditto.services.utils.akkapersistence.mongoaddons

import akka.actor.ActorSystem
import akka.contrib.persistence.mongodb.JournallingFieldNames.{FROM, TAGS, TO}
import akka.contrib.persistence.mongodb.{Atom, CanSuffixCollectionNames, CasbahDriverSettings, CasbahMongoDriver, CasbahPersistenceJournaller, CasbahPersistenceSnapshotter, CasbahSerializers, CasbahSerializersExtension, Event, JournallingFieldNames, MongoPersistenceJournalMetrics}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import com.mongodb.{BasicDBObjectBuilder, MongoCommandException, WriteConcern, MongoClientURI => JavaMongoClientURI}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Class proving the "driver" for the specialized Ditto [[akka.contrib.persistence.mongodb.MongoPersistenceExtension]]
  * extended by the "akka-persistence-mongo" plugin.
  *
  * Use in config like this:
  * {{{
  * akka.contrib.persistence.mongodb.mongo.driver = "org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.DittoCasbahPersistenceExtension"
  * }}}
  *
  * @param actorSystem the Akka system.
  */
class DittoCasbahPersistenceExtension(val actorSystem: ActorSystem) extends DittoMongoPersistenceExtension {

  override def configured(config: Config): Configured = Configured(config)

  case class Configured(config: Config) extends DittoConfiguredExtension {

    override lazy val journaler = new CasbahPersistenceJournaller(driver) with MongoPersistenceJournalMetrics {
      override def driverName = "casbah"
    }
    override lazy val snapshotter = new CasbahPersistenceSnapshotter(driver)
    override lazy val readJournal = new DittoCasbahPersistenceReadJournaller(driver)
    val driver = {
      val theDriver = new DittoCasbahMongoDriver(actorSystem, config)
      // log the driver options
      actorSystem.log.info("DittoCasbahPersistenceExtension created MongoDriver with options <{}>", theDriver.dittoClient.underlying.getMongoClientOptions)
      theDriver
    }
  }

}


private case class IndexSettings(name: String, unique: Boolean, sparse: Boolean, fields: (String, Int)*)

/**
  * Extended by the [[CasbahMongoDriver]] of "akka-persistence-mongo" plugin.
  *
  * Needed as in CasbahMongoDriver most of the methods are package private or completely private.
  */
class DittoCasbahMongoDriver(system: ActorSystem, config: Config) extends CasbahMongoDriver(system, config) {
  import akka.contrib.persistence.mongodb.CasbahPersistenceDriver._

  override val CasbahSerializers: CasbahSerializers = CasbahSerializersExtension(system)

  // Collection type
  override type C = MongoCollection

  override type D = DBObject

  private[mongoaddons] def dittoCloseConnections(): Unit = dittoClient.close()

  private[mongoaddons] def dittoUpgradeJournalIfNeeded: Unit = upgradeDittoJournalIfNeeded("")

  /**
    * retrieve suffix from persistenceId
    */
  private[this] def getSuffixFromPersistenceId(persistenceId: String): String = suffixBuilderClassOption match {
    case Some(suffixBuilderClass) if !suffixBuilderClass.trim.isEmpty =>
      val builderClass = Class.forName(suffixBuilderClass)
      val builderCons = builderClass.getConstructor()
      val builderIns = builderCons.newInstance().asInstanceOf[CanSuffixCollectionNames]
      builderIns.getSuffixFromPersistenceId(persistenceId)
    case _ => ""
  }

  /**
    * validate characters in collection name
    */
  private[this] def validateMongoCharacters(input: String): String = suffixBuilderClassOption match {
    case Some(suffixBuilderClass) if !suffixBuilderClass.trim.isEmpty =>
      val builderClass = Class.forName(suffixBuilderClass)
      val builderCons = builderClass.getConstructor()
      val builderIns = builderCons.newInstance().asInstanceOf[CanSuffixCollectionNames]
      builderIns.validateMongoCharacters(input)
    case _ => input
  }

  /**
    * build name of a collection by appending separator and suffix to usual name in settings
    */
  private[this] def appendSuffixToName(nameInSettings: String)(suffix: String): String = {
    val name =
      suffix match {
        case "" => nameInSettings
        case _  => s"$nameInSettings$suffixSeparator${validateMongoCharacters(suffix)}"
      }
    logger.debug(s"""Suffixed name for value "$nameInSettings" in settings and suffix "$suffix" is "$name"""")
    name
  }

  /**
    * Convenient methods to retrieve journal name from persistenceId
    */
  private[mongoaddons] def getDittoJournalCollectionName(persistenceId: String): String =
    persistenceId match {
      case "" => journalCollectionName
      case _  => appendSuffixToName(journalCollectionName)(getSuffixFromPersistenceId(persistenceId))
    }

  /**
    * Convenient methods to retrieve EXISTING journal collection from persistenceId.
    * CAUTION: this method does NOT create the journal and its indexes.
    */
  private[mongoaddons] def getDittoJournal(persistenceId: String): C = dittoCollection(getDittoJournalCollectionName(persistenceId))

  private[mongoaddons] lazy val dittoIndexes: Seq[IndexSettings] = Seq(
    IndexSettings(journalIndexName, unique = true, sparse = false, JournallingFieldNames.PROCESSOR_ID -> 1, FROM -> 1, TO -> 1),
    IndexSettings(journalSeqNrIndexName, unique = false, sparse = false, JournallingFieldNames.PROCESSOR_ID -> 1, TO -> -1),
    IndexSettings(journalTagIndexName, unique = false, sparse = true, TAGS -> 1)
  )

  private[mongoaddons] lazy val dittoJournal: C = dittoJournal("")

  private[mongoaddons] def dittoJournal(persistenceId: String): C = {
    if (settings.JournalAutomaticUpgrade) {
      logger.debug("Journal automatic upgrade is enabled, executing upgrade process")
      upgradeDittoJournalIfNeeded(persistenceId)
      logger.debug("Journal automatic upgrade process has completed")
    }

    val journalCollection = dittoCollection(getDittoJournalCollectionName(persistenceId))

    dittoIndexes.foldLeft(journalCollection) { (acc, index) =>
      import index._
      dittoEnsureIndex(name, unique, sparse, fields: _*)(concurrent.ExecutionContext.global)(acc)
    }
  }

  private[mongoaddons] def upgradeDittoJournalIfNeeded(persistenceId: String): Unit = {
    import scala.collection.immutable.{ Seq => ISeq }
    import CasbahSerializers._

    val j = getDittoJournal(persistenceId)
    val q = MongoDBObject(VERSION -> MongoDBObject("$exists" -> 0))
    val legacyClusterSharding = MongoDBObject(PROCESSOR_ID -> s"^/user/sharding/[^/]+Coordinator/singleton/coordinator".r)

    Try(j.remove(legacyClusterSharding)).map(
      wr => logger.info(s"Removed ${wr.getN} legacy cluster sharding records as part of upgrade")).recover {
      case x => logger.error("Exception occurred while removing legacy cluster sharding records", x)
    }

    Try(j.dropIndex(MongoDBObject(PROCESSOR_ID -> 1, SEQUENCE_NUMBER -> 1, DELETED -> 1))).orElse(
      Try(j.dropIndex(settings.JournalIndex))).map(
      _ => logger.info("Successfully dropped legacy index")).recover {
      case e: MongoCommandException if e.getErrorMessage.startsWith("index not found with name") =>
        logger.info("Legacy index has already been dropped")
      case t =>
        logger.error("Received error while dropping legacy index", t)
    }

    val cnt = j.count(q)
    logger.info(s"Journal automatic upgrade found $cnt records needing upgrade")
    if (cnt > 0) Try {
      val results = j.find[DBObject](q)
        .map(d => d.as[ObjectId]("_id") -> Event[DBObject](useLegacySerialization)(deserializeJournal(d).toRepr))
        .map { case (id, ev) => j.update("_id" $eq id, serializeJournal(Atom(ev.pid, ev.sn, ev.sn, ISeq(ev)))) }
      results.foldLeft((0, 0)) {
        case ((successes, failures), result) =>
          val n = result.getN
          if (n > 0)
            (successes + n) -> failures
          else
            successes -> (failures + 1)
      } match {
        case (s, f) if f > 0 =>
          logger.warn(s"There were $s successful updates and $f failed updates")
        case (s, _) =>
          logger.info(s"$s records were successfully updated")
      }

    } match {
      case Success(_) => ()
      case Failure(t) =>
        logger.error("Failed to upgrade journal due to exception", t)
    }
  }

  private[this] val casbahSettings = CasbahDriverSettings(system.settings)

  private[this] val url = {
    val underlying =  new JavaMongoClientURI(mongoUri,casbahSettings.mongoClientOptionsBuilder)
    MongoClientURI(underlying)
  }

  private[mongoaddons] lazy val dittoClient = MongoClient(url)

  private[mongoaddons] lazy val dittoDb = dittoClient(databaseName.getOrElse(url.database.getOrElse(DEFAULT_DB_NAME)))

  private[mongoaddons] def dittoCollection(name: String) = dittoDb(name)
  private[mongoaddons] def dittoJournalWriteConcern: WriteConcern = toWriteConcern(journalWriteSafety, journalWTimeout, journalFsync)
  private[mongoaddons] def dittoSnapsWriteConcern: WriteConcern = toWriteConcern(snapsWriteSafety, snapsWTimeout, snapsFsync)
  private[mongoaddons] def dittoMetadataWriteConcern: WriteConcern = toWriteConcern(journalWriteSafety, journalWTimeout, journalFsync)

  private[mongoaddons] def dittoEnsureIndex(indexName: String, unique: Boolean, sparse: Boolean, fields: (String, Int)*)(implicit ec: ExecutionContext): C => C = { collection =>
    collection.createIndex(
      Map(fields: _*),
      Map("unique" -> unique, "sparse" -> sparse, "name" -> indexName))
    collection
  }

  private[mongoaddons] def dittoCappedCollection(name: String)(implicit ec: ExecutionContext) = {
    if (dittoDb.collectionExists(name)) {
      val collection = dittoDb(name)
      if (!collection.isCapped) {
        collection.drop()
        val options = BasicDBObjectBuilder.start.add("capped", true).add("size", realtimeCollectionSize).get()
        dittoDb.createCollection(name, options).asScala
      } else {
        collection
      }
    } else {
      import com.mongodb.casbah.Imports._
      val options = BasicDBObjectBuilder.start.add("capped", true).add("size", realtimeCollectionSize).get()
      val c = dittoDb.createCollection(name, options).asScala
      c.insert(MongoDBObject("x" -> "x")) // casbah cannot tail empty collections
      c
    }
  }

  private[mongoaddons] def getDittoCollections(collectionName: String): List[C] = {
    dittoDb.collectionNames().filter(_.startsWith(collectionName)).map(dittoCollection(_)).toList
  }

  private[mongoaddons] def getDittoJournalCollections(): List[C] = getDittoCollections(journalCollectionName)

  private[mongoaddons] def getDittoSnapshotCollections(): List[C] = getDittoCollections(snapsCollectionName)

}
