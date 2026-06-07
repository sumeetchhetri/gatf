package com.gatf.selenium.trace;

/**
 * Thrown by a {@link BrowserControl} implementation for a capability it does not yet
 * support (e.g. the BiDi transport before parity). Recorders should guard with
 * {@link BrowserControl#supports(Capability)} and never provoke this in normal flow.
 *
 * @author Sumeet Chhetri
 */
public class UnsupportedByTransport extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public UnsupportedByTransport(String message) { super(message); }
}
