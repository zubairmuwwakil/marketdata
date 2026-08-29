package com.zubairmuwwakil.marketdata.guardrails;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Guardrails here read source text, not bytecode. The invariants worth enforcing in
 * this repo are about SQL literals, user-facing copy and endpoint shapes — none of
 * which survive compilation in a form worth asserting against. That is why this
 * milestone uses plain JUnit rather than ArchUnit.
 */
public final class SourceScanner {

    public static final Path MAIN = Path.of("src", "main", "java");

    private SourceScanner() {}

    public static List<Path> mainSources() {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String relative(Path path) {
        return path.toString().replace('\\', '/');
    }
}
