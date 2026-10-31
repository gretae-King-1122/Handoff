package com.handoff.api.links;

/** Local-only platform parser. Input is one clicked URL, never an arbitrary nested URL or chat history. */
public interface LinkParser {
    /** Returns null for unsupported or malformed input. Implementations must not perform I/O. */
    ParsedLink parseUrl(String url);
}
