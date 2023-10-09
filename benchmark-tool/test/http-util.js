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
import { sleep } from 'k6';
import http from 'k6/http';
import * as common from './common.js';
import { formatString } from './str-util.js'

const DITTO_BASE_URI = __ENV.DITTO_BASE_URI;

const DITTO_CONNECTIONS_URI = `${DITTO_BASE_URI}/connections`;
const DITTO_THINGS_URI = `${DITTO_BASE_URI}/things`;
const DITTO_POLICIES_URI = `${DITTO_BASE_URI}/policies`;
const DITTO_SEARCH_THINGS_URI = `${DITTO_BASE_URI}/search/things`

const DITTO_THINGS_MESSAGES_URI_FORMAT = `${DITTO_THINGS_URI}/{0}/inbox/messages/{1}`

const PUSH_ENDPOINT_URI = __ENV.PUSH_ENDPOINT_URI;
const PUSH_ENDPOINT_EVENTS_PATH = __ENV.PUSH_ENDPOINT_EVENTS_PATH
const PUSH_ENDPOINT_LIVE_MESSAGE_PATH = __ENV.PUSH_ENDPOINT_LIVE_MESSAGE_PATH;

const AUTH_CONTEXT = __ENV.DITTO_AUTH_CONTEXT;

const REQUEST_HEADERS = {
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'x-ditto-pre-authenticated': AUTH_CONTEXT,
        'Authorization': __ENV.AUTHORIZATION_HEADER_VALUE
    },
    tags: { name: 'grouped' }
};

const DEVICE_FEATURE = {
    [common.DEVICE_FEATURE_NAME]: {
        'properties': {
            [common.DEVICE_FEATURE_PROPERTY]: 0
        }
    }
};

export function createHttpPushConnection() {
    let connectionBody = constructHttpPushConnection(common.HTTP_PUSH_CONNECTION_CLIENT_COUNT, common.HTTP_PUSH_CONNECTION_PARALLELISM);
    let resp = http.post(DITTO_CONNECTIONS_URI, JSON.stringify(connectionBody), REQUEST_HEADERS);
    let connection = resp.json();

    return connection;
}

export function createKafkaSourceConnection(customAckConnectionId) {
    let connectionBody = constructKafkaSourceConnection(customAckConnectionId);
    let resp = http.post(DITTO_CONNECTIONS_URI, JSON.stringify(connectionBody), REQUEST_HEADERS);
    let connection = resp.json();

    return connection;
}

export function createKafkaTargetConnection() {
    let connectionBody = constructKafkaTargetConnection();
    let resp = http.post(DITTO_CONNECTIONS_URI, JSON.stringify(connectionBody), REQUEST_HEADERS);
    let connection = resp.json();

    return connection;
}

export function waitForConnectionToOpen(connectionId) {
    let connectionOpen;
    while (true) {
        let connectionStatus = http.get(`${DITTO_CONNECTIONS_URI}/${connectionId}/status`, REQUEST_HEADERS).json();

        connectionOpen = 1;
        connectionStatus.clientStatus.forEach((client) => {
            connectionOpen &= (client.status === 'open' && client.statusDetails === 'CONNECTED');
        });

        if (connectionStatus.sourceStatus != undefined) {
            connectionStatus.sourceStatus.forEach((source) => {
                connectionOpen &= (source.status === 'open' && source.statusDetails === 'Consumer started.');
            });
        }

        if (connectionStatus.targetStatus != undefined) {
            connectionStatus.targetStatus.forEach((target) => {
                let statusOpenCheck = target.status === 'open' && target.statusDetails === 'Producer started.';
                let statusUnknownCheck = target.status === 'unknown' && (target.statusDetails.match('.* on-demand') !== undefined);
                connectionOpen &= statusOpenCheck || statusUnknownCheck;
            });
        }

        if (connectionOpen === 1) {
            break;
        }

        sleep(1);
    }
}

export function getThing(id) {
    return http.get(DITTO_THINGS_URI + '/' + id, REQUEST_HEADERS);
}

export function getThingsBatch(ids) {
    let requests = [];
    ids.forEach(id => {
        requests.push({
            'method': 'GET',
            'url': DITTO_THINGS_URI + '/' + id,
            'params': REQUEST_HEADERS
        });
    })

    return http.batch(requests);
}

export function searchThingById(id) {
    return http.get(DITTO_SEARCH_THINGS_URI + getSearchByThingIdFilter(id), REQUEST_HEADERS);
}
export function searchThingByFeature(value) {
    let uri = DITTO_SEARCH_THINGS_URI + getSearchByFeatureFilter(value);
    return http.get(DITTO_SEARCH_THINGS_URI + getSearchByFeatureFilter(value), REQUEST_HEADERS);
}

export function searchThingsBatch(ids) {
    let requests = [];
    ids.forEach(id => {
        requests.push({
            'method': 'GET',
            'url': DITTO_SEARCH_THINGS_URI + getSearchByThingIdFilter(id),
            'params': REQUEST_HEADERS
        });
    })

    return http.batch(requests);
}

export function sendLiveMessageToThing(id, subject, message) {
    return http.post(formatString(DITTO_THINGS_MESSAGES_URI_FORMAT, id, subject), message, REQUEST_HEADERS);
}

export function sendLiveMessageToThingsBatch(ids, subject, message) {
    let requests = [];
    ids.forEach(id => {
        requests.push({
            'method': 'GET',
            'url': formatString(DITTO_THINGS_MESSAGES_URI_FORMAT, id, subject),
            'body': message,
            'params': REQUEST_HEADERS
        });
    })
    return http.batch(requests);
}

export function deleteThing(id) {
    return http.del(DITTO_THINGS_URI + '/' + id, null, REQUEST_HEADERS);
}

export function deletePolicy(id) {
    return http.del(DITTO_POLICIES_URI + '/' + id, null, REQUEST_HEADERS);
}

export function deleteConnection(id) {
    return http.del(DITTO_CONNECTIONS_URI + '/' + id, null, REQUEST_HEADERS);
}

function getSearchByFeatureFilter(value) {
    return `?filter=eq(features/${common.DEVICE_FEATURE_NAME}/properties/${common.DEVICE_FEATURE_PROPERTY},${value})`;
}

function getSearchByThingIdFilter(id) {
    return `?filter=eq(thingId,'${id}')`;
}

function constructHttpPushConnection(clientCount, parallelism) {
    let connectionBody = {
        'name': 'http-push-connection',
        'connectionType': 'http-push',
        'connectionStatus': 'open',
        'uri': PUSH_ENDPOINT_URI,
        'clientCount': clientCount,
        'specificConfig': {
            'parallelism': parallelism
        },
        'sources': [],
        'targets': [
            {
                'address': `POST:${PUSH_ENDPOINT_LIVE_MESSAGE_PATH}`,
                'topics': [
                    '_/_/things/live/messages'
                ],
                'authorizationContext': [AUTH_CONTEXT]
            }
        ],
        'tags': ['benchmark']
    };

    let thingModifiedTarget = {
        'address': `POST:${PUSH_ENDPOINT_EVENTS_PATH}/{{ thing:id }}`,
        'topics': [
            '_/_/things/twin/events?filter=eq(topic:action,\'modified\')'
        ],
        'authorizationContext': [AUTH_CONTEXT]
    };

    if (common.KAFKA_CONNECTION_QOS) {
        if (common.KAFKA_CONNECTION_CUSTOM_ACK != '') {
            thingModifiedTarget['issuedAcknowledgementLabel'] = `{{connection:id}}:${common.KAFKA_CONNECTION_CUSTOM_ACK}`;
        }
    }

    connectionBody['targets'].push(thingModifiedTarget);

    return connectionBody;
}

function constructKafkaSourceConnection(customAckConnectionId) {
    let kafkaConnection = constructKafkaConnection('kafka-source', common.KAFKA_SOURCE_CONNECTION_CLIENT_COUNT);

    kafkaConnection.sources = [
        constructConnectionSource(common.CREATE_UPDATE_THING_SOURCE_TOPIC, AUTH_CONTEXT,
            common.DEVICE_ID_HEADER, common.KAFKA_CONNECTION_CONSUMER_CONSUMER_COUNT, common.KAFKA_CONNECTION_QOS,
            customAckConnectionId, common.KAFKA_CONNECTION_CUSTOM_ACK)
    ];

    kafkaConnection.mappingDefinitions = {
        'implicitThingCreation': {
            'mappingEngine': 'ImplicitThingCreation',
            'options': {
                'thing': constructThingTemplate()
            },
            'incomingConditions': {
                'behindGateway': `fn:filter(header:${common.CREATE_THING_HEADER}, 'exists')`
            }
        },
        'ditto': {
            'mappingEngine': 'Ditto',
            'options': {
                'thingId': `{{ header:${common.DEVICE_ID_HEADER} }}`
            },
            'incomingConditions': {
                'sampleCondition': `fn:filter(header:${common.DITTO_MESSAGE_HEADER},'exists')`
            }
        }
    };
    return kafkaConnection;
}

function constructKafkaTargetConnection() {
    let kafkaConnection = constructKafkaConnection('kafka-reply', common.KAFKA_TARGET_CONNECTION_CLIENT_COUNT);
    kafkaConnection.targets = [
        constructConnectionTarget(common.CREATE_UPDATE_THING_REPLY_TOPIC, AUTH_CONTEXT)
    ];

    return kafkaConnection;
}

function constructKafkaConnection(name, clientCount) {
    return {
        'name': name,
        'connectionType': 'kafka',
        'connectionStatus': 'open',
        'uri': 'tcp://' + common.BOOTSTRAP_SERVERS[0],
        'clientCount': clientCount,
        'processorPoolSize': common.KAFKA_CONNECTION_PROCESSOR_POOL_SIZE,
        'specificConfig': {
            'saslMechanism': 'plain',
            'bootstrapServers': common.BOOTSTRAP_SERVERS.join()
        },
        'tags': ['benchmark']
    }
}

function constructConnectionSource(sourceTopic, authContext, inputHeader, consumerCount, qos, customAckConnectionId, customAck) {
    let connectionSource = {
        'addresses': [
            sourceTopic
        ],
        'consumerCount': consumerCount,
        'qos': qos,
        'authorizationContext': [
            authContext
        ],
        'enforcement': {
            'input': `{{ header:${inputHeader} }}`,
            'filters': [
                '{{ entity:id }}'
            ]
        },
        'payloadMapping': [
            'implicitThingCreation',
            'ditto'
        ],
        'replyTarget': {
            'enabled': false
        }
    };

    if (qos === 1) {
        if (customAckConnectionId !== undefined && (customAck != undefined && customAck != '')) {
            connectionSource['acknowledgementRequests'] = {
                'includes': [`${customAckConnectionId}:${customAck}`]
            };
            connectionSource['declaredAcks'] = [
                `{{connection:id}}:${customAck}`
            ];
        }
    }

    return connectionSource;
}

function constructConnectionTarget(replyTopic, authContext) {
    let connectionTarget = {
        'address': `${replyTopic}/{{ thing:id }}`,
        'topics': [
            '_/_/things/twin/events?filter=eq(topic:action,\'created\')'

        ],
        'authorizationContext': [
            authContext
        ]
    };

    return connectionTarget;
}

function constructThingTemplate() {
    return {
        'thingId': `{{ header:${common.DEVICE_ID_HEADER} }}`,
        '_policy': constructThingPolicy(),
        'definition': 'org.eclipse.ditto:coffeebrewer:0.1.0',
        'attributes': {
            'location': 'test location',
            'model': 'Speaking coffee machine'
        },
        'features': DEVICE_FEATURE
    }
}

function constructThingPolicy() {
    return {
        'entries': {
            'DEVICE': {
                'subjects': {
                    [AUTH_CONTEXT]: {
                        'type': 'does-not-matter'
                    }
                },
                'resources': {
                    'policy:/': {
                        'revoke': [],
                        'grant': [
                            'READ',
                            'WRITE'
                        ]
                    },
                    'thing:/': {
                        'revoke': [],
                        'grant': [
                            'READ',
                            'WRITE'
                        ]
                    },
                    'message:/': {
                        'revoke': [],
                        'grant': [
                            'READ',
                            'WRITE'
                        ]
                    }
                }
            }
        }
    }
}
