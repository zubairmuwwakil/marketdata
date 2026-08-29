package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Provider routing is by purpose, not one global default: curated watchlist goes to
 * Alpha Vantage (sanctioned, 25/day), dynamic per-user symbols to Yahoo (E4,
 * unsanctioned, has headroom). Both are daily closes — the split is sanction and
 * quota, never latency. Injecting a MarketDataProvider directly picks one at wiring
 * time and silently defeats that.
 */
class ProviderResolutionTest {

    private static final Pattern FIELD_DECLARATION = Pattern.compile(
            "\\s*(?:(?:private|protected|public|static|final|transient|volatile)\\s+)*"
                    + "MarketDataProvider\\s+[a-z][A-Za-z0-9]*\\s*(?:=[^;]*)?;");
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+([A-Za-z][A-Za-z0-9]*)\\b");

    @Test
    void noClassInjectsAMarketDataProviderDirectly() {
        Set<String> exempt = ExceptionRegistry.pathsFor("provider-resolution");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(path -> !exempt.contains(SourceScanner.relative(path)))
                .filter(path -> injectsProvider(SourceScanner.read(path)))
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "Resolve providers through MarketDataProviderRegistry by (AssetClass, "
                        + "capability). Injecting one directly picks a provider at wiring "
                        + "time and defeats routing-by-purpose: " + offenders);
    }

    private boolean injectsProvider(String source) {
        if (hasDirectProviderField(source)) {
            return true;
        }

        Matcher classMatcher = CLASS.matcher(source);
        if (!classMatcher.find()) {
            return false;
        }
        String className = classMatcher.group(1);
        Pattern constructor = Pattern.compile(
                "\\b" + Pattern.quote(className) + "\\s*\\([^)]*\\bMarketDataProvider\\s+"
                        + "[a-z][A-Za-z0-9]*[^)]*\\)",
                Pattern.DOTALL);
        return constructor.matcher(source).find();
    }

    private boolean hasDirectProviderField(String source) {
        int depth = 0;
        StringBuilder declaration = new StringBuilder();
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
                if (depth == 2) {
                    declaration.setLength(0);
                }
                continue;
            }
            if (character == '}') {
                if (depth == 2) {
                    declaration.setLength(0);
                }
                depth--;
                continue;
            }
            if (depth == 1) {
                declaration.append(character);
                if (character == ';') {
                    if (FIELD_DECLARATION.matcher(declaration).matches()) {
                        return true;
                    }
                    declaration.setLength(0);
                }
            }
        }
        return false;
    }
}
