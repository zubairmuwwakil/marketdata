package com.zubairmuwwakil.marketdata.model.dto;

public enum QuoteStatus {
    /** Priced as of the most recent closed session. */
    FRESH,
    /** A real last-known close, older than the most recent closed session. Usable,
     *  but the consumer must show its age rather than present it as current. */
    STALE,
    /** No price on file and none obtainable. The close is null — fail closed. */
    UNAVAILABLE
}
