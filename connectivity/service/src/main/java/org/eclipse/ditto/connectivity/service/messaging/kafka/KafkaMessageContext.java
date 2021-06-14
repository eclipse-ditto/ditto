 /*
  * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.connectivity.service.messaging.kafka;

 import java.util.concurrent.CompletionStage;

 import org.apache.kafka.clients.producer.ProducerRecord;

 import akka.kafka.javadsl.SendProducer;

 /**
  * Is used in <code>KafkaPublisherActor</code> for the publishing message stream.
  */
 @FunctionalInterface
 interface KafkaMessageContext {

  CompletionStage<?> onPublishMessage(SendProducer<String, String> sendProducer, ProducerRecord<String, String> record);
 }
