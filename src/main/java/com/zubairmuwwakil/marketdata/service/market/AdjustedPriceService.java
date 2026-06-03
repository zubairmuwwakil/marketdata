package com.zubairmuwwakil.marketdata.service.market;

import com.zubairmuwwakil.marketdata.model.entity.CorporateAction;
import com.zubairmuwwakil.marketdata.model.entity.CorporateActionType;
import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.CorporateActionRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdjustedPriceService {

    public record AdjustedCandle(
            String symbol,
            LocalDate tradeDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            BigDecimal adjustedClose
    ) {}

    private final PriceCandleRepository candleRepository;
    private final CorporateActionRepository actionRepository;

    public AdjustedPriceService(PriceCandleRepository candleRepository,
                                CorporateActionRepository actionRepository) {
        this.candleRepository = candleRepository;
        this.actionRepository = actionRepository;
    }

    @Cacheable(value = "adjustedPrices", key = "#symbol + ':' + #from.toString() + ':' + #to.toString()")
    public List<AdjustedCandle> getAdjustedCandles(String symbol, LocalDate from, LocalDate to) {
        List<PriceCandle> candles = candleRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
        List<CorporateAction> actions = actionRepository.findAllBySymbolOrderByActionDateAsc(symbol);

        List<AdjustedCandle> results = new ArrayList<>();

        for (PriceCandle candle : candles) {
            BigDecimal cumulativeSplit = BigDecimal.ONE;
            for (CorporateAction action : actions) {
                if (action.getActionDate().isAfter(candle.getTradeDate())) {
                    if (action.getActionType() == CorporateActionType.SPLIT && action.getSplitFactor() != null) {
                        cumulativeSplit = cumulativeSplit.multiply(action.getSplitFactor());
                    }
                }
            }

            BigDecimal adjustedClose = candle.getClose();
            if (cumulativeSplit.compareTo(BigDecimal.ZERO) > 0) {
                adjustedClose = candle.getClose().divide(cumulativeSplit, 6, RoundingMode.HALF_UP);
            }

            results.add(new AdjustedCandle(
                    candle.getSymbol(),
                    candle.getTradeDate(),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume(),
                    adjustedClose
            ));
        }

        return results;
    }
}