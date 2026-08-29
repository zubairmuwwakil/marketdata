package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * E3/A5: MarketLens answers "what is this security worth and how has it behaved".
 * Purchases, cards, budgets and the complete financial picture belong to In Unity.
 * The specific line: no endpoint takes quantities and returns a total.
 */
class ServiceBoundaryTest {

    /** Consumer-shaped inputs: a holding size, not a symbol. */
    private static final Pattern QUANTITY_INPUT = Pattern.compile(
            "(?i)\\b(quantity|quantities|shares|units|holdings?|positions?|costBasis|bookValue)\\b");

    private static boolean isController(Path path) {
        return path.getFileName().toString().endsWith("Controller.java");
    }

    /**
     * Endpoint descriptions should be able to explain this boundary. Only Java
     * identifiers reveal an endpoint's actual shape, so ignore prose and comments.
     */
    private static String contractCode(Path path) {
        return SourceScanner.read(path)
                .replaceAll("(?s)\\\"\\\"\\\".*?\\\"\\\"\\\"", "")
                .replaceAll("(?s)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"", "")
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    @Test
    void noControllerAcceptsHoldingQuantities() {
        Set<String> exempt = ExceptionRegistry.pathsFor("service-boundary");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(ServiceBoundaryTest::isController)
                .filter(path -> !exempt.contains(SourceScanner.relative(path)))
                .filter(path -> QUANTITY_INPUT.matcher(contractCode(path)).find())
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "An endpoint taking quantities makes this service own the complete "
                        + "financial picture and welds it to one caller's data model. "
                        + "Symbols in, prices-with-currency-and-staleness out: " + offenders);
    }

    @Test
    void noPortfolioOrValuationRouteExists() {
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(ServiceBoundaryTest::isController)
                .filter(path -> SourceScanner.read(path)
                        .matches("(?is).*@(Request|Get|Post|Put|Patch|Delete)Mapping\\s*\\([^)]*"
                                + "(portfolio|net-?worth|valuation).*"))
                .map(SourceScanner::relative)
                .toList();

        assertTrue(offenders.isEmpty(), "Portfolio valuation is the hub's, not MarketLens': " + offenders);
    }
}
