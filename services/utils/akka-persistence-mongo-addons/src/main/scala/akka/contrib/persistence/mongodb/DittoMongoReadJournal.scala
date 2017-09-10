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

import java.time.Duration

import akka.NotUsed
import akka.actor.{ExtendedActorSystem, Props}
import akka.persistence.query._
import akka.stream.javadsl.{Source => JSource}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config

/**
  * Singleton companion object of [[DittoMongoReadJournal]].
  */
object DittoMongoReadJournal {
  val Identifier = "ditto-akka-persistence-mongo-readjournal"
}

/**
  * Ditto specific ReadJournal queries enhancing the [[MongoReadJournal]] of the "akka-persistence-mongo" library.
  *
  * @param system the Akka system.
  * @param config the config to use.
  */
class DittoMongoReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  private[this] val impl = DittoMongoPersistenceExtension(system)(config).readJournal
  private[this] implicit val materializer = ActorMaterializer()(system)

  override def scaladslReadJournal(): scaladsl.ReadJournal = new DittoScalaDslMongoReadJournal(impl)

  override def javadslReadJournal(): javadsl.ReadJournal = new DittoJavaDslMongoReadJournal(new DittoScalaDslMongoReadJournal(impl))
}

/**
  * Ditto specific ScalaDslMongoReadJournal queries enhancing the [[ScalaDslMongoReadJournal]] of the
  * "akka-persistence-mongo" library.
  *
  * @param impl the DittoMongoPersistenceReadJournallingApi.
  */
class DittoScalaDslMongoReadJournal(impl: DittoMongoPersistenceReadJournallingApi)(implicit m: Materializer) extends ScalaDslMongoReadJournal(impl) {

  /**
    * Query returning stream of the last modified persistenceIds of the passed duration.
    *
    * @param duration the Duration in which to search for the last modified persistenceIds.
    * @return the [[Source]] of [[String]]s containing the last modified peristenceIds.
    */
  def modifiedPidsOfTimespan(duration: Duration): Source[String, NotUsed] =
    Source.actorPublisher[String](impl.modifiedPidsOfTimespan(duration))
      .mapMaterializedValue(_ => NotUsed)

  /**
    * Query returning stream of the persistenceIds and their highest sequence number of the passed list of pids.
    *
    * @param pids the list of persistenceIds for which to return the last sequenceNumbers.
    * @return the [[Source]] of [[PidWithSeqNr]]s containing the peristenceIds with their highest sequence number.
    */
  def sequenceNumbersOfPids(pids: Array[String], offset: Duration): Source[PidWithSeqNr, NotUsed] =
    Source.actorPublisher[PidWithSeqNr](impl.sequenceNumbersOfPids(pids, offset))
      .mapMaterializedValue(_ => NotUsed)

  def sequenceNumbersOfPidsByDuration(duration: Duration, offset: Duration): Source[PidWithSeqNr, NotUsed] =
    Source.actorPublisher[PidWithSeqNr](impl.sequenceNumbersOfPidsByDuration(duration, offset))
      .mapMaterializedValue(_ => NotUsed)
}

/**
  * Ditto specific JavaDslMongoReadJournal queries enhancing the [[JavaDslMongoReadJournal]] of the
  * "akka-persistence-mongo" library.
  *
  * @param rj the DittoScalaDslMongoReadJournal.
  */
class DittoJavaDslMongoReadJournal(rj: DittoScalaDslMongoReadJournal) extends JavaDslMongoReadJournal(rj) {

  /**
    * Query returning stream of the last modified persistenceIds of the passed duration.
    *
    * @param duration the Duration in which to search for the last modified persistenceIds.
    * @return the [[JSource]] of [[String]]s containing the last modified peristenceIds.
    */
  def modifiedPidsOfTimespan(duration: Duration): JSource[String, NotUsed] =
    rj.modifiedPidsOfTimespan(duration).asJava

  /**
    * Query returning stream of the last modified persistenceIds and their highest sequence number of the passed
    * duration.
    *
    * @param pids the list of persistenceIds for which to return the last sequenceNumbers.
    * @return the [[JSource]] of [[String]]s containing the last modified peristenceIds.
    */
  def sequenceNumbersOfPids(pids: Array[String], offset: Duration): JSource[PidWithSeqNr, NotUsed] =
    rj.sequenceNumbersOfPids(pids, offset).asJava

  def sequenceNumbersOfPidsByDuration(duration: Duration, offset: Duration): JSource[PidWithSeqNr, NotUsed] =
    rj.sequenceNumbersOfPidsByDuration(duration, offset).asJava
}

/**
  * Trait containing the additional Ditto specific ReadJournal operations.
  */
trait DittoMongoPersistenceReadJournallingApi extends MongoPersistenceReadJournallingApi {
  def modifiedPidsOfTimespan(duration: Duration): Props

  def sequenceNumbersOfPids(pids: Array[String], offset: Duration): Props

  def sequenceNumbersOfPidsByDuration(duration: Duration, offset: Duration): Props
}
