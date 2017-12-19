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

import java.time.{Duration, Instant}

import akka.NotUsed
import akka.actor.{ExtendedActorSystem, Props}
import akka.contrib.persistence.mongodb.{JavaDslMongoReadJournal, MongoPersistenceReadJournallingApi, ScalaDslMongoReadJournal}
import akka.persistence.query._
import akka.stream.javadsl.{Source => JSource}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config

/**
  * Ditto specific ReadJournal queries enhancing the [[ReadJournalProvider]] of the "akka-persistence-mongo" library.
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
  * Singleton companion object of [[DittoMongoReadJournal]].
  */
object DittoMongoReadJournal {
  val Identifier = "ditto-akka-persistence-mongo-readjournal"
}

/**
  * Ditto specific ScalaDslMongoReadJournal queries enhancing the [[ScalaDslMongoReadJournal]] of the
  * "akka-persistence-mongo" library.
  *
  * @param impl the DittoMongoPersistenceReadJournallingApi.
  */
class DittoScalaDslMongoReadJournal(impl: DittoMongoPersistenceReadJournallingApi)(implicit m: Materializer) extends ScalaDslMongoReadJournal(impl) {

  def sequenceNumbersOfPidsByInterval(start: Instant, end: Instant): Source[PidWithSeqNr, NotUsed] =
    Source.actorPublisher[PidWithSeqNr](impl.sequenceNumbersOfPidsByInterval(start, end))
      .mapMaterializedValue(_ => NotUsed)
}

/**
  * Ditto specific JavaDslMongoReadJournal queries enhancing the [[JavaDslMongoReadJournal]] of the
  * "akka-persistence-mongo" library.
  *
  * @param rj the DittoScalaDslMongoReadJournal.
  */
class DittoJavaDslMongoReadJournal(rj: DittoScalaDslMongoReadJournal) extends JavaDslMongoReadJournal(rj) {

  def sequenceNumbersOfPidsByInterval(start: Instant, end: Instant): JSource[PidWithSeqNr, NotUsed] =
    rj.sequenceNumbersOfPidsByInterval(start, end).asJava
}

/**
  * Trait containing the additional Ditto specific ReadJournal operations.
  */
trait DittoMongoPersistenceReadJournallingApi extends MongoPersistenceReadJournallingApi {

  def sequenceNumbersOfPidsByInterval(start: Instant, end: Instant): Props
}
