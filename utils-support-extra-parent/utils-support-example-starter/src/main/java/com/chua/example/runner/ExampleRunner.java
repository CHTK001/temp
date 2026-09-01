package com.chua.example.runner;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified example dispatcher via SPI.
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ExampleRunner {

    private ExampleRunner() {}

    private static final Map<String, Example> REGISTRY = new java.util.TreeMap<>();

    static {
        for (Example example : ServiceProvider.of(Example.class).collect()) {
            REGISTRY.put(example.name(), example);
        }
    }

    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        List<Example> all = new java.util.ArrayList<>(REGISTRY.values());

        if (parsed.containsKey("example")) {
            String exampleName = parsed.get("example");
            Example target = all.stream()
                    .filter(e -> e.name().equals(exampleName))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                log.error("[FAIL] Example not found: '{}', use --list to see available", exampleName);
                all.forEach(e -> log.error("  --example={}  ({})", e.name(), e.description()));
                System.exit(1);
            }
            log.info("[START] Running example: {} ({})", target.name(), target.description());
            boolean passed = target.run(parsed);
            if (passed) {
                log.info("[PASS] Example '{}' passed all scenarios", target.name());
                System.exit(0);
            } else {
                log.error("[FAIL] Example '{}' has failing scenarios", target.name());
                System.exit(1);
            }
        } else {
            printHelp(all);
        }
    }

    private static void printHelp(List<Example> examples) {
        log.info("Usage: java {} --example=<name> [params...]", ExampleRunner.class.getName());
        log.info("Available examples:");
        log.info("  %-28s %-20s %s", "command", "module", "description");
        log.info("  " + "-".repeat(90));
        for (Example e : examples) {
            log.info("  --example=%-18s %-20s %s", e.name(), e.module(), e.description());
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    map.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else {
                    map.put(arg.substring(2), "");
                }
            } else {
                map.put("_positional_" + map.size(), arg);
            }
        }
        return map;
    }
}