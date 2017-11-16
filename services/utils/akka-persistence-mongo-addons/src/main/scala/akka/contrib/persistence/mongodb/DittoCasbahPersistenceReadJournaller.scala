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

import java.time.{Duration, Instant}
import java.util.{Date, UUID}

import akka.actor.Props
import com.mongodb.BasicDBObject
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId

object MongoDateUtil {

  /**
    * Utility method returning the [[Date]] retrieved by MongoDB minus the passed [[Duration]].
    *
    * @param driver   the CasbahMongoDriver to use.
    * @param duration the Duration to substract.
    * @return the calulated Date from MongoDB minus the passed [[Duration]].
    */
  def retrieveCurrentMongoDateMinusDuration(driver: CasbahMongoDriver, duration: Duration): Date =
    new MongoDateUtil(driver).retrieveCurrentMongoDateMinusDuration(duration)
}

class MongoDateUtil(val driver: CasbahMongoDriver) {
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
    val upsertedId = driver.collection(TEST_COLLECTION_NAME).update(query, update, upsert = true, multi = false, WRITE_CONCERN).getUpsertedId
    val mongoDate = driver.collection(TEST_COLLECTION_NAME).findOneByID(upsertedId).get(DATE_FIELD).asInstanceOf[Date]
    driver.collection(TEST_COLLECTION_NAME).remove(MongoDBObject(MONGO_ID -> upsertedId), WRITE_CONCERN)
    Date.from(mongoDate.toInstant.minusMillis(duration.toMillis))
  }
}

/**
  * Factory for class [[ModifiedPidsOfTimespan]].
  */
object ModifiedPidsOfTimespan {
  def props(driver: CasbahMongoDriver, duration: Duration): Props =
    Props(new ModifiedPidsOfTimespan(driver, duration))
}

/**
  * Class providing the implementation for retrieving the modified persistenceIds of a given Duration as a Stream of
  * [[String]]s.
  *
  * @param driver   the CasbahMongoDriver to use.
  * @param duration the Duration.
  */
class ModifiedPidsOfTimespan(val driver: CasbahMongoDriver, duration: Duration)
  extends SyncActorPublisher[String, Stream[String]] {

  import JournallingFieldNames._

  val searchDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, duration)

  override protected def initialCursor: Stream[String] =
    driver.journal
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
  def props(driver: CasbahMongoDriver, pids: Array[String], offset: Duration): Props =
    Props(new SequenceNumbersOfPids(driver, pids, offset))
}

/**
  * Class providing the implementation for retrieving the highest sequence numbers for the modified persistenceIds as a
  * Stream of [[PidWithSeqNr]]s.
  *
  * @param driver the CasbahMongoDriver to use.
  * @param pids   the persistenceIds to retrieve the highest sequenceNumber for.
  */
class SequenceNumbersOfPids(val driver: CasbahMongoDriver, pids: Array[String], offset: Duration)
  extends SyncActorPublisher[PidWithSeqNr, Stream[PidWithSeqNr]] {

  import JournallingFieldNames._

  val offsetDate = MongoDateUtil.retrieveCurrentMongoDateMinusDuration(driver, offset)

  override protected def initialCursor: Stream[PidWithSeqNr] =
    driver.journal
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
object SequenceNumbersOfPidsByInterval {
  def props(driver: CasbahMongoDriver, start: Instant, end: Instant): Props =
    Props(new SequenceNumbersOfPidsByInterval(driver, start, end))
}


/**
  * Class providing the implementation for retrieving sequence numbers for persistenceIds modified within the time
  * interval as a Stream of [[PidWithSeqNr]]s. A persistenceId may appear multiple times in the stream with various
  * sequence numbers.
  *
  * @param driver the CasbahMongoDriver to use.
  * @param start  start of the time window.
  * @param end    end of the time window.
  */
class SequenceNumbersOfPidsByInterval(val driver: CasbahMongoDriver, start: Instant, end: Instant)
  extends SyncActorPublisher[PidWithSeqNr, Stream[PidWithSeqNr]] {

  import JournallingFieldNames._

  // MongoDB 3.4 object ID consists of:
  // - 4 byte: number of epoch seconds
  // - 3 byte: machine ID
  // - 2 byte: process ID
  // - 3 byte: counter
  //
  // The epoch seconds field is allowed to overflow after year 2038.
  // The epoch seconds field will no longer reflect the insert time after year 2075.
  //
  // The object ID bounds need to be aware of overflow.
  //
  def getMongoEpochSecond(instant: Instant): Int = {
    val epochSeconds: Long = instant.getEpochSecond
    if (epochSeconds > 0xffffffffL) {
      val message = s"Year 2106 problem: MongoDB object ID timestamp field overflows unsigned Int! <$instant>"
      throw new IllegalArgumentException(message)
    } else {
      epochSeconds.toInt
    }
  }

  override protected def initialCursor: Stream[PidWithSeqNr] = {
    val lowerBound: Int = getMongoEpochSecond(start)
    val upperBound: Int = getMongoEpochSecond(end.plus(Duration.ofSeconds(1)))
    driver.journal
      .aggregate(
        List(
          MongoDBObject("$match" -> MongoDBObject(
            "_id" -> MongoDBObject(
              "$gte" -> new ObjectId(lowerBound, 0, 0.toShort, 0),
              "$lt" -> new ObjectId(upperBound, 0, 0.toShort, 0))
          )),
          MongoDBObject("$project" -> MongoDBObject(
            PROCESSOR_ID -> true,
            SEQUENCE_NUMBER -> s"$$$EVENTS.$SEQUENCE_NUMBER"
          ))
        ),
        AggregationOptions(AggregationOptions.CURSOR)
      )
      .toStream
      .map(foo => PidWithSeqNr(foo.getAs[String](PROCESSOR_ID).get, foo.getAs[Long](SEQUENCE_NUMBER).get))
  }

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
  * @param driver the CasbahMongoDriver to use.
  */
class DittoCasbahPersistenceReadJournaller(driver: CasbahMongoDriver) extends CasbahPersistenceReadJournaller(driver) with DittoMongoPersistenceReadJournallingApi {
  override def modifiedPidsOfTimespan(duration: Duration): Props =
    ModifiedPidsOfTimespan.props(driver, duration)

  override def sequenceNumbersOfPids(pids: Array[String], offset: Duration): Props =
    SequenceNumbersOfPids.props(driver, pids, offset)

  override def sequenceNumbersOfPidsByInterval(start: Instant, end: Instant): Props =
    SequenceNumbersOfPidsByInterval.props(driver, start, end)
}


