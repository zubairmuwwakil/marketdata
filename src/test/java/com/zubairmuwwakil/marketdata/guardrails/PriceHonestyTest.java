package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A6, the honesty invariant. This service serves daily closes. Saying "real-time"
 * anywhere a user can read it — API copy, the dashboard, OpenAPI descriptions — is
 * a claim the infrastructure does not support.
 */
class PriceHonestyTest {

    private static final Pattern CLAIM = Pattern.compile("(?i)real[\\s-]?time|live price|live quote");
    private static final Pattern DENIAL =
            Pattern.compile("(?i)\\b(not|never|no|rather than|instead of)\\b[^.]{0,24}real[\\s-]?time");

    @Test
    void nothingUserFacingClaimsRealTimePricing() {
        List<String> offenders = new ArrayList<>();
        for (Path path : userFacingSources()) {
            String[] lines = SourceScanner.read(path).split("\\n");
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                String bare = line.strip();
                if (bare.startsWith("//") || bare.startsWith("*") || bare.startsWith("/*")
                        || bare.startsWith("#") || bare.startsWith("<!--")) {
                    continue;
                }
                if (CLAIM.matcher(line).find() && !DENIAL.matcher(line).find()) {
                    offenders.add(SourceScanner.relative(path) + ":" + (index + 1));
                }
            }
        }

        assertTrue(
                offenders.isEmpty(),
                "This service serves daily closes. Say \"daily close\" or \"latest close\": "
                        + offenders);
    }

    private static List<Path> userFacingSources() {
        List<Path> paths = new ArrayList<>(SourceScanner.mainSources());
        Path resources = Path.of("src", "main", "resources");
        if (!Files.exists(resources)) {
            return paths;
        }
        try (Stream<Path> walk = Files.walk(resources)) {
            walk.filter(path -> {
                        String name = path.toString();
                        return name.endsWith(".html") || name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .forEach(paths::add);
            return paths;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
