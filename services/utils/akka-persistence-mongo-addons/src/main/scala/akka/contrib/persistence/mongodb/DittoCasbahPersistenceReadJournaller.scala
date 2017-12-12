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

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.Date

import akka.actor.Props
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId

/**
  * Factory for class [[SequenceNumbersOfPidsByInterval]].
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

  override protected def initialCursor: Stream[PidWithSeqNr] = {
    /* MongoDBObject IDs only contain dates with precision of seconds, thus adjust the range of the query
       appropriately to make sure a client does not miss data when providing Instants with higher precision
     */
    val startTruncatedToSecs = start.truncatedTo(ChronoUnit.SECONDS)
    val endTruncatedToSecs = end.truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofSeconds(1))

    log.debug("Getting modified PIDs from {} to {}", startTruncatedToSecs, endTruncatedToSecs)

    /* Do not use

         new ObjectId(Date.from(startTruncatedToSecs))

       to compute object ID boundaries. The 1-argument constructor above appends incidental non-zero bits after
       the timestamp and may filter out events persisted after 'startTruncatedToSecs' if they happen to have
       a lower machine ID, process ID or counter value. (A MongoDB ObjectID is a byte array with fields for timestamp,
       machine ID, process ID and counter such that timestamp occupies the most significant bits.)
     */
    val startObjectId: ObjectId = instantToObjectIdBoundary(startTruncatedToSecs)
    val endObjectId: ObjectId = instantToObjectIdBoundary(endTruncatedToSecs)

    log.debug("Limiting query to ObjectIds $gte {} and $lt {}", startObjectId, endObjectId)

    val filterObject: DBObject = DBObject(ID -> DBObject("$gte" -> startObjectId, "$lt" -> endObjectId))

    val projectObject: DBObject = DBObject(PROCESSOR_ID -> 1, TO -> 1)

    val sortObject: DBObject = DBObject(ID -> -1)

    driver.journal
      .find(filterObject, projectObject)
      .sort(sortObject)
      .toStream
      .map(foo => PidWithSeqNr(foo.getAs[String](PROCESSOR_ID).get,
        foo.getAs[Long](TO).get))
  }

  override protected def next(c: Stream[PidWithSeqNr], atMost: Long): (Vector[PidWithSeqNr], Stream[PidWithSeqNr]) = {
    val (buf, remainder) = c.splitAt(atMost.toIntWithoutWrapping)
    (buf.toVector, remainder)
  }

  override protected def isCompleted(c: Stream[PidWithSeqNr]): Boolean = {
    c.isEmpty
  }

  override protected def discard(c: Stream[PidWithSeqNr]): Unit = ()

  /* Create a ObjectID boundary from a timestamp to be used for comparison in MongoDB queries. */
  private[this] def instantToObjectIdBoundary(instant: Instant): ObjectId = {
    new ObjectId(Date.from(instant), 0, 0.toShort, 0)
  }
}

/**
  * Implementation of Trait DittoMongoPersistenceReadJournallingApi providing implementation for Ditto specific Event-Journal
  * queries.
  *
  * @param driver the CasbahMongoDriver to use.
  */
class DittoCasbahPersistenceReadJournaller(driver: CasbahMongoDriver) extends CasbahPersistenceReadJournaller(driver) with DittoMongoPersistenceReadJournallingApi {

  override def sequenceNumbersOfPidsByInterval(start: Instant, end: Instant): Props =
    SequenceNumbersOfPidsByInterval.props(driver, start, end)
}


