package com.zubairmuwwakil.marketdata.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "marketdata.demo")
public class DemoProperties {

    private boolean enabled;
    private int lookbackDays = 170;
    private String featuredSymbol = "MSFT";
    private String qualitySymbol = "TSLA";
    private String actionSymbol = "NVDA";
    private List<String> activeSymbols = new ArrayList<>(List.of("MSFT", "AAPL", "NVDA", "SPY"));
    private List<String> inactiveSymbols = new ArrayList<>(List.of("TSLA"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public String getFeaturedSymbol() {
        return featuredSymbol;
    }

    public void setFeaturedSymbol(String featuredSymbol) {
        this.featuredSymbol = featuredSymbol;
    }

    public String getQualitySymbol() {
        return qualitySymbol;
    }

    public void setQualitySymbol(String qualitySymbol) {
        this.qualitySymbol = qualitySymbol;
    }

    public String getActionSymbol() {
        return actionSymbol;
    }

    public void setActionSymbol(String actionSymbol) {
        this.actionSymbol = actionSymbol;
    }

    public List<String> getActiveSymbols() {
        return activeSymbols;
    }

    public void setActiveSymbols(List<String> activeSymbols) {
        this.activeSymbols = activeSymbols == null ? new ArrayList<>() : new ArrayList<>(activeSymbols);
    }

    public List<String> getInactiveSymbols() {
        return inactiveSymbols;
    }

    public void setInactiveSymbols(List<String> inactiveSymbols) {
        this.inactiveSymbols = inactiveSymbols == null ? new ArrayList<>() : new ArrayList<>(inactiveSymbols);
    }
}
