package com.zubairmuwwakil.marketdata.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A batch of quotes plus the yardstick they are measured against.
 *
 * @param pricing         always {@code "daily-close"}. Stated in the payload rather
 *                        than left to documentation, because a consumer that
 *                        mislabels this as real-time in its own UI breaks the
 *                        honesty invariant on our behalf.
 * @param expectedSession most recent session that has closed; what FRESH means.
 * @param truncated       symbols dropped because the batch exceeded the per-request
 *                        cap. Reported rather than silently trimmed.
 */
public record QuoteBatch(
        String pricing,
        LocalDate expectedSession,
        List<SymbolQuote> quotes,
        List<String> truncated
) {}
