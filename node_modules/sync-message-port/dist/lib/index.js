"use strict";
// Copyright 2021 Google LLC. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
Object.defineProperty(exports, "__esModule", { value: true });
exports.SyncMessagePort = exports.TimeoutException = void 0;
const events_1 = require("events");
const worker_threads_1 = require("worker_threads");
const atomic_counter_1 = require("./atomic_counter");
/**
 * An exception thrown by {@link SyncMessagePort.receiveMessage} if a message
 * isn't received within {@link ReceivedMessageOptions.timeout} milliseconds.
 */
class TimeoutException extends Error {
    constructor(message) {
        super(message);
    }
}
exports.TimeoutException = TimeoutException;
/**
 * A communication port that can receive messages synchronously from another
 * `SyncMessagePort`.
 *
 * This also emits the same asynchronous events as `MessagePort`. Messages are
 * preferentially sent to {@link receiveMessage} if a call to it is outstanding,
 * and only sent to the event handler if they weren't received synchronously.
 */
class SyncMessagePort extends events_1.EventEmitter {
    port;
    /** Creates a channel whose ports can be passed to `new SyncMessagePort()`. */
    static createChannel() {
        const channel = new worker_threads_1.MessageChannel();
        // 16 bytes is required for `AtomicCounter`.
        const buffer1 = new SharedArrayBuffer(16);
        const buffer2 = new SharedArrayBuffer(16);
        // Queue up messages on each port so the caller doesn't have to explicitly
        // pass the buffer around along with them.
        channel.port1.postMessage(buffer1);
        channel.port1.postMessage(buffer2);
        channel.port2.postMessage(buffer2);
        channel.port2.postMessage(buffer1);
        return channel;
    }
    /**
     * An atomic counter of messages posted yet to be received.
     */
    postCounter;
    /**
     * An atomic counter of messages available to be received.
     */
    receiveCounter;
    /**
     * Creates a new message port. The `port` must be created by
     * `SyncMessagePort.createChannel()` and must connect to a port passed to
     * another `SyncMessagePort` in another worker.
     */
    constructor(port) {
        super();
        this.port = port;
        const buffer1 = (0, worker_threads_1.receiveMessageOnPort)(this.port)?.message;
        const buffer2 = (0, worker_threads_1.receiveMessageOnPort)(this.port)?.message;
        if (!buffer1 || !buffer2) {
            throw new Error('new SyncMessagePort() must be passed a port from ' +
                'SyncMessagePort.createChannel().');
        }
        this.postCounter = new atomic_counter_1.AtomicCounter(buffer1);
        this.receiveCounter = new atomic_counter_1.AtomicCounter(buffer2);
        const messageHandler = () => {
            this.receiveCounter.wait();
            this.receiveCounter.decrement();
        };
        this.port.on('messageerror', (error) => {
            messageHandler();
            if (!this.listenerCount('messageerror')) {
                throw error;
            }
        });
        this.on('newListener', (event, listener) => {
            if (event === 'message' && !this.listenerCount(event)) {
                this.port.on(event, messageHandler);
            }
            this.port.on(event, listener);
        });
        this.on('removeListener', (event, listener) => {
            this.port.removeListener(event, listener);
            if (event === 'message' && !this.listenerCount(event)) {
                this.port.removeListener(event, messageHandler);
            }
        });
    }
    /** See `MessagePort.postMesage()`. */
    postMessage(value, transferList) {
        // @ts-expect-error: TypeScript gets confused with the overloads.
        this.port.postMessage(value, transferList);
        this.postCounter.increment();
    }
    /**
     * Returns the message sent by the other port, if one is available. This *does
     * not* block, and will return `undefined` immediately if no message is
     * available. In order to distinguish between a message with value `undefined`
     * and no message, a message is return in an object with a `message` field.
     *
     * It does *not* throw an error if the port is closed when this is called;
     * instead, it just returns `undefined`.
     */
    receiveMessageIfAvailable() {
        const message = (0, worker_threads_1.receiveMessageOnPort)(this.port);
        if (message) {
            this.receiveCounter.wait();
            this.receiveCounter.decrement();
        }
        return message;
    }
    /**
     * Blocks and returns the next message sent by the other port.
     *
     * Throws an error if the channel is closed and all messages are drained,
     * including if it closes while this is waiting for a message, unless
     * {@link ReceiveMessageOptions.closedValue} is passed.
     */
    receiveMessage(options) {
        if (!this.receiveCounter.wait(options?.timeout)) {
            if ('timeoutValue' in options)
                return options.timeoutValue;
            throw new TimeoutException('SyncMessagePort.receiveMessage() timed out.');
        }
        const message = (0, worker_threads_1.receiveMessageOnPort)(this.port);
        if (message) {
            this.receiveCounter.decrement();
            return message.message;
        }
        // The port is closed and all remaining messages are drained.
        if (options && 'closedValue' in options)
            return options.closedValue;
        throw new Error("The SyncMessagePort's channel is closed.");
    }
    /** See `MessagePort.close()`. */
    close() {
        this.port.close();
        this.postCounter.close();
        this.receiveCounter.close();
    }
}
exports.SyncMessagePort = SyncMessagePort;
//# sourceMappingURL=index.js.map