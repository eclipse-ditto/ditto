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
    DittoNodeClient, DittoWebSocketLiveClient, DittoWebSocketTwinClient,
    WebSocketBuilderInitialStep, WebSocketChannelStep, WebSocketBufferStep, AuthenticationStep,
    WebSocketStateHandler, DefaultMatchOptions, DefaultMessagesOptions, EnvironmentStep
} from '@eclipse-ditto/ditto-javascript-client-node';
import { getConfig } from '../common';
import { getAuthProvider } from '../ditto-auth-util';
import { debugLog } from '../utils';
import { ThingModifier, LiveMessenger } from '../interfaces';

/**
 * WebSocket Channel - twin channel (modifyThing) and live channel (sendLiveMessage)
 */
export class WebSocketChannel implements ThingModifier, LiveMessenger {
    private static instance: WebSocketChannel | null = null;
    private useTls: boolean;
    private baseUri: string;

    // Shared connection pool per worker process (not per VU)
    private sharedTwinClient: DittoWebSocketTwinClient | null = null;
    private sharedLiveClient: DittoWebSocketLiveClient | null = null;
    private twinClientPromise: Promise<DittoWebSocketTwinClient> | null = null;
    private liveClientPromise: Promise<DittoWebSocketLiveClient> | null = null;

    private constructor() {
        const config = getConfig();
        this.baseUri = config.ditto.baseUri;
        this.useTls = config.ditto.protocol.ws === 'wss';
    }

    public static getInstance(): WebSocketChannel {
        if (!WebSocketChannel.instance) {
            WebSocketChannel.instance = new WebSocketChannel();
        }
        return WebSocketChannel.instance;
    }

    public static resetInstance(): void {
        if (WebSocketChannel.instance) {
            WebSocketChannel.instance.closeAllWebSocketConnections();
            WebSocketChannel.instance = null;
        }
    }

    async getTwinClient(): Promise<DittoWebSocketTwinClient> {
        if (this.sharedTwinClient) {
            debugLog('[WebSocket Twin] Reusing shared client');
            return this.sharedTwinClient;
        }

        if (this.twinClientPromise) {
            return this.twinClientPromise;
        }

        debugLog('[WebSocket Twin] Creating shared connection...');
        this.twinClientPromise = this.createTwinClient();

        try {
            this.sharedTwinClient = await this.twinClientPromise;
            return this.sharedTwinClient;
        } catch (error) {
            this.twinClientPromise = null;
            throw error;
        }
    }

    private async createTwinClient(): Promise<DittoWebSocketTwinClient> {
        const wsUri = `${getConfig().ditto.protocol.ws}://${this.baseUri}/ws/2`;
        debugLog(`[WebSocket Twin Channel] Connecting to ${wsUri}...`);

        const { handler, connectionPromise } = this.createStateHandler();

        const client = this.createClientCommonBuilder()
            .twinChannel()
            .withStateHandler(handler)
            .build();

        try {
            await connectionPromise;
            debugLog('[WebSocket Twin Channel] Connected');
            return client;
        } catch (error: any) {
            console.error('[WebSocket Twin Channel] Failed to connect:', error.message);
            throw error;
        }
    }

    async getLiveClient(): Promise<DittoWebSocketLiveClient> {
        if (this.sharedLiveClient) {
            debugLog('[WebSocket Live] Reusing shared client');
            return this.sharedLiveClient;
        }

        if (this.liveClientPromise) {
            return this.liveClientPromise;
        }

        debugLog('[WebSocket Live] Creating shared connection...');
        this.liveClientPromise = this.createLiveClient();

        try {
            this.sharedLiveClient = await this.liveClientPromise;
            return this.sharedLiveClient;
        } catch (error) {
            this.liveClientPromise = null;
            throw error;
        }
    }

    private async createLiveClient(): Promise<DittoWebSocketLiveClient> {
        const wsUri = `${getConfig().ditto.protocol.ws}://${this.baseUri}/ws/2`;
        debugLog(`[WebSocket Live Channel] Connecting to ${wsUri}...`);

        const { handler, connectionPromise } = this.createStateHandler();

        const client = this.createClientCommonBuilder()
            .liveChannel()
            .withStateHandler(handler)
            .build();

        try {
            await connectionPromise;
            debugLog('[WebSocket Live Channel] Connected');
            return client;
        } catch (error: any) {
            console.error('[WebSocket Live Channel] Failed to connect:', error.message);
            throw error;
        }
    }

    private createStateHandler(): { handler: WebSocketStateHandler; connectionPromise: Promise<void> } {
        let resolveConnection: () => void;
        let rejectConnection: (reason: Error) => void;

        const connectionPromise = new Promise<void>((resolve, reject) => {
            resolveConnection = resolve;
            rejectConnection = reject;

            setTimeout(() => {
                reject(new Error('WebSocket connection timeout (10s)'));
            }, 10000);
        });

        const handler: WebSocketStateHandler = {
            connected: () => resolveConnection(),
            buffering: () => { },
            backPressure: () => { },
            reconnecting: () => { },
            bufferFull: () => { },
            disconnected: () => rejectConnection(new Error('WebSocket disconnected'))
        };

        return { handler, connectionPromise };
    }

    private createClientCommonBuilder(): WebSocketChannelStep {
        const clientBuilder: WebSocketBuilderInitialStep = DittoNodeClient.newWebSocketClient();
        let envStep: EnvironmentStep<WebSocketBufferStep>;

        if (this.useTls) {
            envStep = clientBuilder.withTls();
        } else {
            envStep = clientBuilder.withoutTls();
        }
        let authStep: AuthenticationStep<WebSocketBufferStep> = envStep.withDomain(this.baseUri);

        // Use getAuthProvider() which prefers basic auth if configured, otherwise falls back to pre-auth header
        const auth = getAuthProvider();
        const bufferStep: WebSocketBufferStep = authStep.withAuthProvider(auth);

        // Use withReconnect(false) if reconnect causes hangs
        // return bufferStep.withReconnect(false).withoutBuffer();

        return bufferStep.withoutBuffer();
    }

    async modifyThing({ thingId, updates, timeout }: { thingId: string; updates: { path: string; value: any }; timeout?: number }): Promise<any> {
        if (!this.sharedTwinClient) {
            throw new Error('[WebSocket Channel] Twin channel not connected');
        }

        const options = DefaultMatchOptions.getInstance();
        if (timeout !== undefined) {
            options.addHeader('timeout', timeout.toString());
        }
        return await this.sharedTwinClient.getThingsHandle()
            .putAttribute(thingId, updates.path, updates.value, options);
    }

    async sendLiveMessage({ thingId, subject, payload, timeout }: { thingId: string; subject: string; payload: any; timeout?: number }): Promise<any> {
        if (!this.sharedLiveClient) {
            throw new Error('[WebSocket Channel] Live channel not connected');
        }

        const options = DefaultMessagesOptions.getInstance();
        if (timeout !== undefined) {
            options.addHeader('timeout', timeout.toString());
        }
        return await this.sharedLiveClient.getMessagesHandle()
            .messageToThing(thingId, subject, payload, "json", options);
    }

    /**
     * Close all WebSocket connections for this instance
     */
    closeAllWebSocketConnections(): void {
        if (this.sharedTwinClient) {
            try {
                this.sharedTwinClient.close(1000, 'close');
            } catch (error: any) {
                console.error('[WebSocket] Error closing twin client:', error.message);
            }
            this.sharedTwinClient = null;
            this.twinClientPromise = null;
        }

        if (this.sharedLiveClient) {
            try {
                this.sharedLiveClient.close(1000, 'close');
            } catch (error: any) {
                console.error('[WebSocket] Error closing live client:', error.message);
            }
            this.sharedLiveClient = null;
            this.liveClientPromise = null;
        }
    }
}
