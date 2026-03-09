/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
    DittoNodeClient,
    DefaultMatchOptions,
    DefaultSearchOptions,
    Thing,
    DittoHttpClient,
    DefaultMessagesOptions,
    GenericResponse
} from '@eclipse-ditto/ditto-javascript-client-node';
import {
    getConfig,
    DEVICE_FEATURE_NAME,
    DEVICE_FEATURE_PROPERTY,
    DEVICE_ID_HEADER,
    CREATE_THING_HEADER,
    DITTO_MESSAGE_HEADER
} from './common';
import { getAuthProvider, getAuthHeaders } from './ditto-auth-util';
import { debugLog } from './utils';
import { getKafkaBootstrapServers } from './config';

function getDittoBaseUriFull(): string {
    const config = getConfig();
    return `${config.ditto.protocol.http}://${config.ditto.baseUri}`;
}

function getDittoConnectionsUri(): string {
    return `${getDittoBaseUriFull()}/api/2/connections`;
}

function getPreAuthenticatedValue(): string {
    return getConfig().ditto.auth.preAuthenticatedHeaderValue || '';
}

// Ditto client instance
let dittoClient: DittoHttpClient | null = null;

// Device feature template
const DEVICE_FEATURE: any = {
    [DEVICE_FEATURE_NAME]: {
        properties: {
            [DEVICE_FEATURE_PROPERTY]: 0
        }
    }
};

function parseDomainFromUri(uri: string): string {
    const match = uri.match(/^https?:\/\/([^/]+)/);
    return match ? match[1] : uri;
}

function shouldUseTls(uri: string): boolean {
    return uri.startsWith('https://');
}

async function initializeDittoClient() {
    if (dittoClient) {
        return dittoClient;
    }

    debugLog('[Ditto Client] Initializing...');

    const baseUriFull = getDittoBaseUriFull();
    const domain = parseDomainFromUri(baseUriFull);
    const useTls = shouldUseTls(baseUriFull);

    let clientBuilder: any = DittoNodeClient.newHttpClient();

    if (useTls) {
        clientBuilder = clientBuilder.withTls();
    } else {
        clientBuilder = clientBuilder.withoutTls();
    }

    clientBuilder = clientBuilder.withDomain(domain);

    const auth = getAuthProvider();
    clientBuilder = clientBuilder.withAuthProvider(auth).withTimeout(getConfig().ditto.httpTimeoutMs);

    dittoClient = clientBuilder.build();
    debugLog('[Ditto Client] Initialized');
    return dittoClient;
}

function getDefaultOptions(): any {
    const options = DefaultMatchOptions.getInstance();
    getAuthHeaders().forEach((value, key) => {
        options.addHeader(key, value);
    });
    return options;
}

function getDefaultMessageOptions(timeout?: number): DefaultMessagesOptions {
    const options = timeout !== undefined
        ? DefaultMessagesOptions.getInstance().withTimeout(timeout)
        : DefaultMessagesOptions.getInstance();
    getAuthHeaders().forEach((value, key) => {
        options.addHeader(key, value);
    });
    return options;
}

function getDevopsHeaders(): Record<string, string> {
    const config = getConfig();
    const { devopsUser, devopsPassword } = config.ditto.auth;
    return {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': `Basic ${btoa(`${devopsUser}:${devopsPassword}`)}`
    };
}

// ======================
// Connection Management (using fetch)
// ======================

async function fetchWithTimeout(url: string, options: RequestInit): Promise<Response> {
    return await fetch(url, {
        ...options,
        signal: AbortSignal.timeout(getConfig().ditto.httpTimeoutMs)
    });
}

async function createHttpPushConnection(): Promise<any> {
    console.log('[Ditto] Creating HTTP Push connection...');
    const config = getConfig();
    const connectionBody = constructHttpPushConnection(
        config.connections.httpPush.clientCount,
        config.connections.httpPush.parallelism
    );

    const response = await fetchWithTimeout(getDittoConnectionsUri(), {
        method: 'POST',
        headers: getDevopsHeaders(),
        body: JSON.stringify(connectionBody)
    });

    if (!response.ok) {
        throw new Error(`Failed to create HTTP Push connection: ${response.status} ${response.statusText}`);
    }

    const connection: any = await response.json();
    console.log(`[Ditto] HTTP Push connection created: ${connection.id}`);
    return connection;
}

async function createKafkaSourceConnection(customAckConnectionId?: string): Promise<any> {
    console.log('[Ditto] Creating Kafka Source connection...');
    const connectionBody = constructKafkaSourceConnection(customAckConnectionId);

    const response = await fetchWithTimeout(getDittoConnectionsUri(), {
        method: 'POST',
        headers: getDevopsHeaders(),
        body: JSON.stringify(connectionBody)
    });

    if (!response.ok) {
        throw new Error(`Failed to create Kafka Source connection: ${response.status} ${response.statusText}`);
    }

    const connection: any = await response.json();
    console.log(`[Ditto] Kafka Source connection created: ${connection.id}`);
    return connection;
}

async function createKafkaTargetConnection(): Promise<any> {
    console.log('[Ditto] Creating Kafka Target connection...');
    const connectionBody = constructKafkaTargetConnection();

    const response = await fetchWithTimeout(getDittoConnectionsUri(), {
        method: 'POST',
        headers: getDevopsHeaders(),
        body: JSON.stringify(connectionBody)
    });

    if (!response.ok) {
        throw new Error(`Failed to create Kafka Target connection: ${response.status} ${response.statusText}`);
    }

    const connection: any = await response.json();
    console.log(`[Ditto] Kafka Target connection created: ${connection.id}`);
    return connection;
}

async function waitForConnectionToOpen(connectionId: string): Promise<any> {
    const maxRetries = getConfig().connections.openMaxRetries;
    console.log(`[Ditto] Waiting for connection ${connectionId} to open (max ${maxRetries} retries)...`);

    for (let i = 0; i < maxRetries; i++) {
        const response = await fetchWithTimeout(`${getDittoConnectionsUri()}/${connectionId}/status`, {
            method: 'GET',
            headers: getDevopsHeaders()
        });

        if (!response.ok) {
            throw new Error(`Failed to get connection status: ${response.status} ${response.statusText}`);
        }

        const connectionStatus: any = await response.json();
        let connectionOpen = true;
        const statusDetails: string[] = [];

        if (connectionStatus.clientStatus) {
            connectionStatus.clientStatus.forEach((client: any, idx: number) => {
                const isOpen = client.status === 'open' && client.statusDetails === 'CONNECTED';
                connectionOpen = connectionOpen && isOpen;
                statusDetails.push(`Client[${idx}]: ${client.status} - ${client.statusDetails}`);
            });
        }

        if (connectionStatus.sourceStatus) {
            connectionStatus.sourceStatus.forEach((source: any, idx: number) => {
                const isOpen = source.status === 'open' && source.statusDetails === 'Consumer started.';
                connectionOpen = connectionOpen && isOpen;
                statusDetails.push(`Source[${idx}]: ${source.status} - ${source.statusDetails}`);
            });
        }

        if (connectionStatus.targetStatus) {
            connectionStatus.targetStatus.forEach((target: any, idx: number) => {
                const statusOpenCheck = target.status === 'open' && target.statusDetails === 'Producer started.';
                const statusUnknownCheck = target.status === 'unknown' && target.statusDetails && target.statusDetails.includes('on-demand');
                const isOpen = statusOpenCheck || statusUnknownCheck;
                connectionOpen = connectionOpen && isOpen;
                statusDetails.push(`Target[${idx}]: ${target.status} - ${target.statusDetails}`);
            });
        }

        if (connectionOpen) {
            console.log(`[Ditto] Connection ${connectionId} is OPEN`);
            return connectionStatus;
        }

        debugLog(`[Ditto] Connection ${connectionId} not ready (attempt ${i + 1}/${maxRetries})`);
        debugLog(`[Ditto] Connection status: ${JSON.stringify(connectionStatus,null,2)}`);
        await new Promise(resolve => setTimeout(resolve, 1000));
    }

    throw new Error(`Connection ${connectionId} failed to open after ${maxRetries} retries`);
}

async function deleteConnection(id: string): Promise<void> {
    const response = await fetchWithTimeout(`${getDittoConnectionsUri()}/${id}`, {
        method: 'DELETE',
        headers: getDevopsHeaders()
    });

    if (!response.ok && response.status !== 404) {
        throw new Error(`Failed to delete connection: ${response.status} ${response.statusText}`);
    }
}

// ======================
// Thing Operations (using Ditto client)
// ======================

async function getThing(id: string): Promise<Thing> {
    const client = await initializeDittoClient();
    try {
        return await client!.getThingsHandle().getThing(id);
    } catch (error: any) {
        console.error(`[HTTP] Failed to get thing ${id}:`, error.message);
        throw error;
    }
}

async function getThings(ids: string[]): Promise<Thing[]> {
    const client = await initializeDittoClient();
    try {
        return await client!.getThingsHandle().getThings(ids);
    } catch (error: any) {
        console.error(`[HTTP] Failed to get things:`, error.message);
        throw error;
    }
}

async function modifyThingAttribute(thingId: string, attributePath: string, value: any, timeout?: number): Promise<any> {
    const client = await initializeDittoClient();
    try {
        const options = getDefaultOptions();
        if (timeout !== undefined) {
            options.addRequestParameter('timeout', timeout.toString());
        }
        return await client!.getThingsHandle().putAttribute(thingId, attributePath, value, options);
    } catch (error: any) {
        console.error(`[HTTP] Failed to modify thing ${thingId}:`, error.message);
        throw error;
    }
}

async function searchThingById(id: string): Promise<any> {
    const client = await initializeDittoClient();
    const searchHandle = client!.getSearchHandle();

    const searchOptions = DefaultSearchOptions.getInstance()
        .withFilter(`eq(thingId,'${id}')`);
    getAuthHeaders().forEach((value, key) => {
        searchOptions.addHeader(key, value);
    });

    try {
        return await searchHandle.search(searchOptions);
    } catch (error: any) {
        console.error(`[HTTP] Failed to search for thing ${id}:`, error.message);
        throw error;
    }
}

// ======================
// Live Messages
// ======================

async function sendLiveMessageToThing(id: string, subject: string, message: any, timeout?: number): Promise<GenericResponse> {
    const client = await initializeDittoClient();
    const messagesHandle = client!.getMessagesHandle();

    const payload = typeof message === 'string' ? message : JSON.stringify(message);
    const contentType = typeof message === 'string' ? 'text/plain' : 'application/json';

    return await messagesHandle.messageToThing(
        id,
        subject,
        payload,
        contentType,
        getDefaultMessageOptions(timeout)
    );
}

// ======================
// Delete Operations
// ======================

async function deleteThing(id: string): Promise<any> {
    const client = await initializeDittoClient();
    return await client!.getThingsHandle().deleteThing(id, getDefaultOptions());
}

async function deletePolicy(id: string): Promise<any> {
    const client = await initializeDittoClient();
    return await client!.getPoliciesHandle().deletePolicy(id, getDefaultOptions());
}

// ======================
// Connection Construction Helpers
// ======================

function constructHttpPushConnection(clientCount: number, parallelism: number): any {
    const config = getConfig();
    const authContext = getPreAuthenticatedValue();
    const httpPushConfig = config.connections.httpPush;
    console.log(httpPushConfig.targets);
    // Build targets from config
    //bugs maybe we change the original config here
    const targets = httpPushConfig.targets
        .filter(targetConfig => targetConfig.topics.length > 0)
        .map(targetConfig => {
            const target: any = {
                address: targetConfig.address,
                topics: targetConfig.topics,
                authorizationContext: [authContext]
            };

            if (targetConfig.issuedAcknowledgementLabel) {
                target.issuedAcknowledgementLabel = targetConfig.issuedAcknowledgementLabel;
            }

            return target;
        });
    console.log(targets);

    return {
        name: 'http-push-connection',
        connectionType: 'http-push',
        connectionStatus: 'open',
        uri: httpPushConfig.uri,
        clientCount: clientCount,
        specificConfig: {
            parallelism: parallelism
        },
        sources: [],
        targets: targets,
        tags: ['benchmark']
    };
}

function constructKafkaSourceConnection(customAckConnectionId?: string): any {
    const config = getConfig();
    const authContext = getPreAuthenticatedValue();
    const kafkaConfig = config.connections.kafka;

    const kafkaConnection = constructKafkaConnection(kafkaConfig.sourceConnectionName, kafkaConfig.sourceClientCount);

    kafkaConnection.sources = [
        constructConnectionSource(
            kafkaConfig.topics.createUpdateThing.source.name,
            authContext,
            DEVICE_ID_HEADER,
            kafkaConfig.consumerCount,
            kafkaConfig.qos,
            customAckConnectionId,
            kafkaConfig.customAck
        )
    ];

    kafkaConnection.mappingDefinitions = {
        implicitThingCreation: {
            mappingEngine: 'ImplicitThingCreation',
            options: {
                thing: constructThingTemplate()
            },
            incomingConditions: {
                behindGateway: `fn:filter(header:${CREATE_THING_HEADER}, 'exists')`
            }
        },
        ditto: {
            mappingEngine: 'Ditto',
            options: {
                thingId: `{{ header:${DEVICE_ID_HEADER} }}`
            },
            incomingConditions: {
                sampleCondition: `fn:filter(header:${DITTO_MESSAGE_HEADER},'exists')`
            }
        }
    };

    return kafkaConnection;
}

function constructKafkaTargetConnection(): any {
    const config = getConfig();
    const authContext = getPreAuthenticatedValue();
    const kafkaConfig = config.connections.kafka;

    const kafkaConnection = constructKafkaConnection(kafkaConfig.targetConnectionName, kafkaConfig.targetClientCount);

    kafkaConnection.targets = [
        constructConnectionTarget(kafkaConfig.topics.createUpdateThing.reply.name, authContext)
    ];

    return kafkaConnection;
}

function constructKafkaConnection(name: string, clientCount: number): any {
    const bootstrapServers = getKafkaBootstrapServers(getConfig());
    const kafkaConfig = getConfig().connections.kafka;

    return {
        name: name,
        connectionType: 'kafka',
        connectionStatus: 'open',
        uri: 'tcp://' + bootstrapServers[0],
        clientCount: clientCount,
        processorPoolSize: kafkaConfig.processorPoolSize,
        specificConfig: {
            saslMechanism: 'plain',
            bootstrapServers: bootstrapServers.join(',')
        },
        tags: ['benchmark']
    };
}

function constructConnectionSource(
    sourceTopic: string,
    authContext: string,
    inputHeader: string,
    consumerCount: number,
    qos: number,
    customAckConnectionId?: string,
    customAck?: string
): any {
    const connectionSource: any = {
        addresses: [sourceTopic],
        consumerCount: consumerCount,
        qos: qos,
        authorizationContext: [authContext],
        enforcement: {
            input: `{{ header:${inputHeader} }}`,
            filters: ['{{ entity:id }}']
        },
        payloadMapping: ['implicitThingCreation', 'ditto'],
        replyTarget: {
            enabled: false
        }
    };

    if (qos === 1) {
        if (customAckConnectionId !== undefined && customAck !== undefined && customAck !== '') {
            connectionSource.acknowledgementRequests = {
                includes: [`${customAckConnectionId}:${customAck}`]
            };
            connectionSource.declaredAcks = [`{{connection:id}}:${customAck}`];
        }
    }

    return connectionSource;
}

function constructConnectionTarget(replyTopic: string, authContext: string): any {
    return {
        address: `${replyTopic}/{{ thing:id }}`,
        topics: ["_/_/things/twin/events?filter=eq(topic:action,'created')"],
        authorizationContext: [authContext]
    };
}

function constructThingTemplate(): any {
    return {
        thingId: `{{ header:${DEVICE_ID_HEADER} }}`,
        _policy: constructThingPolicy(),
        definition: 'org.eclipse.ditto:coffeebrewer:0.1.0',
        attributes: {
            location: 'test location',
            model: 'Speaking coffee machine'
        },
        features: DEVICE_FEATURE
    };
}

function constructThingPolicy(): any {
    const authContext = getPreAuthenticatedValue();
    return {
        entries: {
            DEVICE: {
                subjects: {
                    [authContext]: {
                        type: 'does-not-matter'
                    }
                },
                resources: {
                    'policy:/': {
                        revoke: [],
                        grant: ['READ', 'WRITE']
                    },
                    'thing:/': {
                        revoke: [],
                        grant: ['READ', 'WRITE']
                    },
                    'message:/': {
                        revoke: [],
                        grant: ['READ', 'WRITE']
                    }
                }
            }
        }
    };
}

export {
    initializeDittoClient,
    createHttpPushConnection,
    createKafkaSourceConnection,
    createKafkaTargetConnection,
    waitForConnectionToOpen,
    deleteConnection,
    getThing,
    getThings as getThingsBatch,
    modifyThingAttribute,
    searchThingById,
    sendLiveMessageToThing,
    deleteThing,
    deletePolicy
};
