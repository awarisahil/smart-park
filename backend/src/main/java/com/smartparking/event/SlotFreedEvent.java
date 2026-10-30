package com.smartparking.event;

/**
 * Published after a slot returns to the available pool (cancel, check-out,
 * expiry). The waitlist service listens for it and auto-allocates queued users.
 */
public record SlotFreedEvent() {
}
