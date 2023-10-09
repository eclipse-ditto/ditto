/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import {
    Writer,
    Connection
} from 'k6/x/kafka';

import {
    SchemaRegistry,
} from 'k6/x/kafka';

// KAFKA RELATED
export const BOOTSTRAP_SERVERS = __ENV.KAFKA_BOOTSTRAP_SERVERS.split(',');

export const CREATE_UPDATE_THING_SOURCE_TOPIC = __ENV.CREATE_UPDATE_THING_SOURCE_TOPIC;
export const CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS = parseInt(__ENV.CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS);
export const CREATE_UPDATE_THING_REPLY_TOPIC = __ENV.CREATE_UPDATE_THING_REPLY_TOPIC;
export const CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS = parseInt(__ENV.CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS);
export const CREATE_UPDATE_THING_CONSUMER_GROUP_ID = __ENV.CREATE_UPDATE_THING_CONSUMER_GROUP_ID;

let producer = undefined;
producer = new Writer({
    brokers: BOOTSTRAP_SERVERS,
    topic: CREATE_UPDATE_THING_SOURCE_TOPIC,
    connectLogger: __ENV.KAFKA_PRODUCER_LOGGER_ENABLED == 1,
});
// if (__VU === 1) {
//     // execute only once
//     createThingsTopicsIfNotAvailable();
// }

export const KAFKA_CREATE_UPDATE_PRODUCER = producer;

export const KAFKA_CONNECTION_QOS = parseInt(__ENV.KAFKA_CONNECTION_QOS);
export const KAFKA_CONNECTION_CUSTOM_ACK = __ENV.KAFKA_CONNECTION_CUSTOM_ACK;
export const KAFKA_CONNECTION_CONSUMER_CONSUMER_COUNT = parseInt(__ENV.KAFKA_CONNECTION_CONSUMER_CONSUMER_COUNT);
export const KAFKA_SOURCE_CONNECTION_CLIENT_COUNT = parseInt(__ENV.KAFKA_SOURCE_CONNECTION_CLIENT_COUNT);
export const KAFKA_TARGET_CONNECTION_CLIENT_COUNT = parseInt(__ENV.KAFKA_TARGET_CONNECTION_CLIENT_COUNT);
export const KAFKA_CONNECTION_PROCESSOR_POOL_SIZE = parseInt(__ENV.KAFKA_CONNECTION_PROCESSOR_POOL_SIZE);

// KAFKA RELATED

// HTTP PUSH CONNECTION
export const HTTP_PUSH_CONNECTION_CLIENT_COUNT = parseInt(__ENV.HTTP_PUSH_CONNECTION_CLIENT_COUNT);
export const HTTP_PUSH_CONNECTION_PARALLELISM = parseInt(__ENV.HTTP_PUSH_CONNECTION_PARALLELISM);
// HTTP PUSH CONNECTION

// DITTO RELATED
export const DEVICE_NAMESPACE = __ENV.DEVICE_NAMESPACE;
export const DEVICE_ID_TEMPLATE = `${DEVICE_NAMESPACE}:${__ENV.DEVICE_ID_TEMPLATE}`;
export const THINGS_COUNT = parseInt(__ENV.THINGS_COUNT);
export const BATCH_SIZE = parseInt(__ENV.BATCH_SIZE);
export const PRODUCE_THINGS_BATCH_SIZE = parseInt(__ENV.PRODUCE_THINGS_BATCH_SIZE);

export const THINGS_START_INDEX = parseInt(__ENV.THINGS_START_INDEX);
export function GET_THING_ID(index) {
    return DEVICE_ID_TEMPLATE + (index + THINGS_START_INDEX);
}

export const DEVICE_FEATURE_NAME = 'coffee-brewer';
export const DEVICE_FEATURE_PROPERTY = 'brewed-coffees';
export const DEVICE_ID_HEADER = 'device_id';
export const CREATE_THING_HEADER = 'create_thing';
export const DITTO_MESSAGE_HEADER = 'ditto_message';
// DITTO RELATED

// K6 RELATED
export const schemaRegistry = new SchemaRegistry();
// K6 RELATED

// SCENARIOS RELATED
export const THINGS_WARMUP_VUS = parseInt(__ENV.THINGS_WARMUP_VUS);
// SCENARIOS RELATED

export function createThingsTopicsIfNotAvailable() {
    let connection;
    try {
        connection = new Connection({ address: BOOTSTRAP_SERVERS[0] });
        let availableTopics = connection.listTopics();
        console.log(availableTopics);
        if (availableTopics.indexOf(CREATE_UPDATE_THING_SOURCE_TOPIC) === -1) {
            console.log(`creating topic ${CREATE_UPDATE_THING_SOURCE_TOPIC}`);
            connection.createTopic({
                topic: CREATE_UPDATE_THING_SOURCE_TOPIC,
                numPartitions: CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS
            });
        }

        if (availableTopics.indexOf(CREATE_UPDATE_THING_REPLY_TOPIC) === -1) {
            console.log(`creating topic ${CREATE_UPDATE_THING_REPLY_TOPIC}`);
            connection.createTopic({
                topic: CREATE_UPDATE_THING_REPLY_TOPIC,
                numPartitions: CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS
            });
        }
    } finally {
        if (connection !== undefined) {
            connection.close();
        }
    }
}

export function deleteTopics() {
    let connection;
    try {
        connection = new Connection({ address: BOOTSTRAP_SERVERS[0] });
        connection.deleteTopic(CREATE_UPDATE_THING_SOURCE_TOPIC);
        connection.deleteTopic(CREATE_UPDATE_THING_REPLY_TOPIC);
    } finally {
        if (connection !== undefined) {
            connection.close();
        }
    }
}
