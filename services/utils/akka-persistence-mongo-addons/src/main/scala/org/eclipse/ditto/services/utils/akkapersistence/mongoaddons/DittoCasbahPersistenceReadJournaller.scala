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

import java.time.Duration
import java.util.{Date, UUID}

import akka.actor.Props
import akka.contrib.persistence.mongodb.{CasbahPersistenceReadJournaller, SyncActorPublisher}
import com.mongodb.BasicDBObject
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId

object MongoDateUtil {

  /**
    * Utility method returning the [[Date]] retrieved by MongoDB minus the passed [[Duration]].
    *
    * @param driver   the DittoCasbahMongoDriver to use.
    * @param duration the Duration to substract.
    * @return the calculated Date from MongoDB minus the passed [[Duration]].
    */
  def retrieveCurrentMongoDateMinusDuration(driver: DittoCasbahMongoDriver, duration: Duration): Date =
    new MongoDateUtil(driver).retrieveCurrentMongoDateMinusDuration(duration)
}

class MongoDateUtil(val driver: DittoCasbahMongoDriver) {
  val TEST_COLLECTION_NAME = "test"
  val CURRENT_DATE_FUNC = "$currentDate"
  val DATE_FIELD = "date"
  val MONGO_ID = "_id"
  val WRITE_CONCERN = WriteConcern.Acknowledged

  def retrieveCurrentMongoDateMinusDuration(duration: Duration): Date = {
    val query = new BasicDBObject(MONGO_ID, UUID.randomUUID().toString)
    // should never match
    val update = new BasicDBObject(CURRENT_DATE_FUNC,
      new BasicDBObject(DATE_FIELD, true)
    )
    val upsertedId = driver.dittoCollection(TEST_COLLECTION_NAME).update(query, update, upsert = true, multi = false, WRITE_CONCERN).getUpsertedId
    val mongoDate = driver.dittoCollection(TEST_COLLECTION_NAME).findOneByID(upsertedId).get(DATE_FIELD).asInstanceOf[Date]
    driver.dittoCollection(TEST_COLLECTION_NAME).remove(MongoDBObject(MONGO_ID -> upsertedId), WRITE_CONCERN)
    Date.from(mongoDate.toInstant.minusMillis(duration.toMillis))
  }
}

/**
  * Factory for class [[ModifiedPidsOfTimespan]].
  */
object ModifiedPidsOfTimespan {
  def props(driver: DittoCasbahMongoDriver, duration: Duration): Props =
    Props(new ModifiedPidsOfTimespan(driver, duration))
}

/**
  * Class providing the implementation for retrieving the modified persistenceIds of a given Duration as a Stream of
  * [[String]]s.
  *
  * @param driver   the DittoCasbahMongoDriver to use.
  * @param duration the Duration.
  */
class ModifiedPidsOfTimespan(val driver: DittoCasbahMongoDriver, duration: Duration)
  extends SyncActorPublisher[String, Stream[String]] {

  import akka.contrib.persistence.mongodb.JournallingFieldNames._

  val searchDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, duration)

  override protected def initialCursor: Stream[String] =
    driver.dittoJournal
      .distinct(PROCESSOR_ID, MongoDBObject("_id" -> MongoDBObject("$gte" -> new ObjectId(searchDate))))
      .toStream
      .map(any => any.asInstanceOf[String])

  override protected def next(c: Stream[String], atMost: Long): (Vector[String], Stream[String]) = {
    val (buf, remainder) = c.splitAt(atMost.toIntWithoutWrapping)
    (buf.toVector, remainder)
  }

  override protected def isCompleted(c: Stream[String]): Boolean = {
    c.isEmpty
  }

  override protected def discard(c: Stream[String]): Unit = ()
}

/**
  * Factory for class [[SequenceNumbersOfPids]].
  */
object SequenceNumbersOfPids {
  def props(driver: DittoCasbahMongoDriver, pids: Array[String], offset: Duration): Props =
    Props(new SequenceNumbersOfPids(driver, pids, offset))
}

/**
  * Class providing the implementation for retrieving the highest sequence numbers for the modified persistenceIds as a
  * Stream of [[PidWithSeqNr]]s.
  *
  * @param driver the DittoCasbahMongoDriver to use.
  * @param pids   the persistenceIds to retrieve the highest sequenceNumber for.
  */
class SequenceNumbersOfPids(val driver: DittoCasbahMongoDriver, pids: Array[String], offset: Duration)
  extends SyncActorPublisher[PidWithSeqNr, Stream[PidWithSeqNr]] {

  import akka.contrib.persistence.mongodb.JournallingFieldNames._

  val offsetDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, offset)

  override protected def initialCursor: Stream[PidWithSeqNr] =
    driver.dittoJournal
      .aggregate(List(
        MongoDBObject("$match" -> MongoDBObject(
          PROCESSOR_ID -> MongoDBObject("$in" -> pids))
        ),
        MongoDBObject("$sort" -> MongoDBObject(TO -> -1)),
        MongoDBObject("$group" -> MongoDBObject(
          "_id" -> "$".concat(PROCESSOR_ID),
          "maxSeqNr" -> MongoDBObject("$first" -> "$".concat(TO)),
          "oId" -> MongoDBObject("$first" -> "$_id")))
        ,
        MongoDBObject("$match" -> MongoDBObject(
          "oId" -> MongoDBObject("$lte" -> new ObjectId(offsetDate)))
        )
      )
        ,
        AggregationOptions(AggregationOptions.CURSOR)
      )
      .toStream
      .map(foo => PidWithSeqNr(foo.getAs[String]("_id").get, foo.getAs[Long]("maxSeqNr").get))

  override protected def next(c: Stream[PidWithSeqNr], atMost: Long): (Vector[PidWithSeqNr], Stream[PidWithSeqNr]) = {
    val (buf, remainder) = c.splitAt(atMost.toIntWithoutWrapping)
    (buf.toVector, remainder)
  }

  override protected def isCompleted(c: Stream[PidWithSeqNr]): Boolean = {
    c.isEmpty
  }

  override protected def discard(c: Stream[PidWithSeqNr]): Unit = ()
}

/**
  * Factory for class [[SequenceNumbersOfPids]].
  */
object SequenceNumbersOfPidsByDuration {
  def props(driver: DittoCasbahMongoDriver, duration: Duration, offset: Duration): Props =
    Props(new SequenceNumbersOfPidsByDuration(driver, duration, offset))
}


/**
  * Class providing the implementation for retrieving the highest sequence numbers for the modified persistenceIds as a
  * Stream of [[PidWithSeqNr]]s.
  *
  * @param driver the DittoCasbahMongoDriver to use.
  * @param duration   the persistenceIds to retrieve the highest sequenceNumber for.
  */
class SequenceNumbersOfPidsByDuration(val driver: DittoCasbahMongoDriver, duration: Duration, offset: Duration)
  extends SyncActorPublisher[PidWithSeqNr, Stream[PidWithSeqNr]] {

  import akka.contrib.persistence.mongodb.JournallingFieldNames._

  val offsetDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, offset)
  val searchDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, duration)

  override protected def initialCursor: Stream[PidWithSeqNr] =
    driver.dittoJournal
      .aggregate(List(
        MongoDBObject("$match" -> MongoDBObject(
          "_id" -> MongoDBObject("$gte" -> new ObjectId(searchDate)))
        ),
        MongoDBObject("$sort" -> MongoDBObject(TO -> -1)),
        MongoDBObject("$group" -> MongoDBObject(
          "_id" -> "$".concat(PROCESSOR_ID),
          "maxSeqNr" -> MongoDBObject("$first" -> "$".concat(TO)),
          "oId" -> MongoDBObject("$first" -> "$_id")))
        ,
        MongoDBObject("$match" -> MongoDBObject(
          "oId" -> MongoDBObject("$lte" -> new ObjectId(offsetDate)))
        )
      )
        ,
        AggregationOptions(AggregationOptions.CURSOR)
      )
      .toStream
      .map(foo => PidWithSeqNr(foo.getAs[String]("_id").get, foo.getAs[Long]("maxSeqNr").get))

  override protected def next(c: Stream[PidWithSeqNr], atMost: Long): (Vector[PidWithSeqNr], Stream[PidWithSeqNr]) = {
    val (buf, remainder) = c.splitAt(atMost.toIntWithoutWrapping)
    (buf.toVector, remainder)
  }

  override protected def isCompleted(c: Stream[PidWithSeqNr]): Boolean = {
    c.isEmpty
  }

  override protected def discard(c: Stream[PidWithSeqNr]): Unit = ()
}

/**
  * Implementation of Trait DittoMongoPersistenceReadJournallingApi providing implementation for Ditto specific Event-Journal
  * queries.
  *
  * @param driver the DittoCasbahMongoDriver to use.
  */
class DittoCasbahPersistenceReadJournaller(driver: DittoCasbahMongoDriver) extends CasbahPersistenceReadJournaller(driver) with DittoMongoPersistenceReadJournallingApi {
  override def modifiedPidsOfTimespan(duration: Duration): Props =
    ModifiedPidsOfTimespan.props(driver, duration)

  override def sequenceNumbersOfPids(pids: Array[String], offset: Duration): Props =
    SequenceNumbersOfPids.props(driver, pids, offset)

  override def sequenceNumbersOfPidsByDuration(duration: Duration, offset: Duration): Props =
    SequenceNumbersOfPidsByDuration.props(driver, duration, offset)
}


