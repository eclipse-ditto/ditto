/**
 * A counter that can be atomically incremented and decremented, and which
 * callers can synchronously listen for it becoming non-zero.
 *
 * This can be "open" or "closed"; once it's closed, {@link wait} will no longer
 * emit useful events.
 */
export declare class AtomicCounter {
    /**
     * The underlying BigInt64Array.
     *
     * The first BigInt64 represents the current value of the counter. The second
     * BigInt64 is used to track the closed state.
     */
    private readonly buffer;
    constructor(buffer: SharedArrayBuffer);
    /** Atomically decrement the current value by one. */
    decrement(): void;
    /** Atomically increment the current value by one. */
    increment(): void;
    /**
     * Closes the counter.
     *
     * This will cause any outstanding calls to {@link wait} on any thread to
     * return `true` immediately.
     */
    close(): void;
    /**
     * Waits until the current value is not zero or the counter is closed.
     *
     * Returns `true` when the counter is non-zero *or* when it's closed. Returns
     * `false` if the counter remains zero for `timeout` milliseconds.
     */
    wait(timeout?: number): boolean;
}
