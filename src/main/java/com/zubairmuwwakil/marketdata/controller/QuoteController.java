package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.QuoteBatch;
import com.zubairmuwwakil.marketdata.repository.TrackedSymbolRepository;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import com.zubairmuwwakil.marketdata.service.quote.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Quotes", description = "Latest daily closes for an arbitrary symbol set")
public class QuoteController {

    private final QuoteService quoteService;
    private final TrackedSymbolRepository trackedSymbolRepository;

    public QuoteController(QuoteService quoteService, TrackedSymbolRepository trackedSymbolRepository) {
        this.quoteService = quoteService;
        this.trackedSymbolRepository = trackedSymbolRepository;
    }

    @Operation(
            summary = "Latest daily close for each requested symbol",
            description = """
                    Daily closing prices — **not real-time**, and never described as such.

                    Symbols need not be on the curated watchlist: anything asked for here is
                    demand-registered and kept warm by the nightly sweep.

                    Every quote carries a `status`:
                    * `FRESH` — priced as of `expectedSession`, the most recent closed session.
                    * `STALE` — a real last-known close, older than `expectedSession`, with the
                      gap in `staleTradingDays`. Usable, but show its age.
                    * `UNAVAILABLE` — no price on file and none obtainable. `close` is null.
                      Never interpolated and never zero-filled, so a consumer fails closed
                      instead of reporting a fabricated number.

                    `currency` is the ISO-4217 the price is quoted in, or null when no provider
                    reported one. Prices in different currencies must not be summed; this
                    service returns each price in its own currency and does not convert.

                    Bring your own key: send `X-Provider-Key: PROVIDER=yourkey` (comma-separated
                    for several) to have upstream calls made under your own credential and quota.
                    It is used for the request and never stored. `keySource` on each quote reports
                    whether your key (`USER`), this service's key (`APP`), or a keyless source
                    (`NONE`) actually supplied the price.
                    """
    )
    @GetMapping
    public QuoteBatch quotes(
            @Parameter(description = "Comma-separated symbols, e.g. AAPL,MSFT,VFV.TO", example = "AAPL,MSFT")
            @RequestParam String symbols,
            @Parameter(description = "Instrument type to resolve a provider for")
            @RequestParam(name = "assetClass", defaultValue = "EQUITY") AssetClass assetClass
    ) {
        List<String> requested = Arrays.stream(symbols.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return quoteService.quote(requested, assetClass);
    }

    @Operation(
            summary = "Symbols consumers have asked about",
            description = """
                    The demand-registered set, distinct from the curated watchlist. Holds
                    symbols only — no consumer identity, no quantities, no user ids.
                    """
    )
    @GetMapping("/tracked")
    public List<TrackedSymbolRepository.TrackedSymbol> tracked() {
        return trackedSymbolRepository.findAll();
    }

    /** Documents the BYOK header for tooling; the value itself is never echoed. */
    @Operation(hidden = true)
    @GetMapping("/byok-header")
    public String byokHeader() {
        return ProviderCredentials.HEADER;
    }
}
