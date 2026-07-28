package su.knst.crypto.tests.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import su.knst.crypto.Main;
import su.knst.crypto.command.CommandResult;
import su.knst.crypto.command.ParamsContainer;
import su.knst.crypto.command.commands.hex.HexCommand;
import su.knst.crypto.command.commands.seed.SeedGeneratorCommand;
import su.knst.crypto.command.commands.seed.SeedToBaseCommand;
import su.knst.crypto.command.commands.shamir.ShamirCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the whole seed-backup workflow driven only through the public Command layer,
 * the same way a real user would go through it on the CLI:
 * <p>
 * entropy -&gt; mnemonic (seed) -&gt; back to entropy (seed_to_base) -&gt; hex-encoded backup file
 * -&gt; Shamir split -&gt; Shamir join (from many different subsets of parts) -&gt; hex-decoded back
 * -&gt; mnemonic again, verifying every step round-trips to exactly the same data.
 * <p>
 * Runs the pipeline across multiple entropy sizes (12-word and 24-word seeds) and multiple
 * Shamir (n, k) configurations, and for each split, joins from several different random subsets
 * of the required size to make sure recovery does not depend on which particular parts survive.
 */
class SeedBackupWorkflowTest {

    private static final Pattern NUMBERED_WORD_LINE = Pattern.compile("^\\d+\\.\\s+(\\S+)$");
    private static final SecureRandom RANDOM = new SecureRandom();

    Main main;
    List<Path> filesToCleanUp;

    @BeforeEach
    void setUp() {
        main = new Main(); // don't start because user terminal not needed
        filesToCleanUp = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : filesToCleanUp)
            Files.deleteIfExists(path);
    }

    static Stream<Arguments> variants() {
        // entropyBytes -> expected mnemonic word count is 16 -> 12 words, 32 -> 24 words
        int[] entropySizes = {16, 32};
        // allParts, forRecover
        int[][] shamirSchemes = {{2, 2}, {3, 2}, {5, 3}, {7, 4}, {5, 5}};

        List<Arguments> args = new ArrayList<>();
        for (int entropySize : entropySizes)
            for (int[] scheme : shamirSchemes)
                args.add(Arguments.of(entropySize, scheme[0], scheme[1]));

        return args.stream();
    }

    @ParameterizedTest(name = "{0}-byte entropy, shamir {1}-of parts / {2} required")
    @MethodSource("variants")
    void fullWorkflow(int entropySize, int allParts, int forRecover) throws Exception {
        String runId = entropySize + "_" + allParts + "_" + forRecover;
        int expectedWords = entropySize == 32 ? 24 : 12;

        // 1. Generate a seed phrase from fresh entropy, exactly like `seed` on the CLI.
        byte[] sourceEntropy = new byte[entropySize];
        RANDOM.nextBytes(sourceEntropy);

        SeedGeneratorCommand seedCommand = main.getHandler().getCommand(SeedGeneratorCommand.class).orElseThrow();
        CommandResult genResult = seedCommand.run(sourceEntropy);
        assertFalse(genResult.error(), "seed generation failed: " + genResult.message());

        List<String> mnemonic = extractMnemonicWords(genResult.message(), expectedWords);
        assertEquals(expectedWords, mnemonic.size());

        // 2. Feed the mnemonic back through `seed_to_base`, like a user restoring from words,
        // and check it reproduces the exact same entropy that was used to generate it.
        SeedToBaseCommand seedToBaseCommand = main.getHandler().getCommand(SeedToBaseCommand.class).orElseThrow();
        CommandResult toBaseResult = seedToBaseCommand.run(new ParamsContainer(mnemonic));
        assertFalse(toBaseResult.error(), "seed_to_base failed: " + toBaseResult.message());
        assertArrayEquals(sourceEntropy, extractBase64Entropy(toBaseResult.message()));

        // 3. Write the entropy to a "backup" file and hex-encode/decode it, like `hex encode`/`hex decode`.
        Path entropyFile = Path.of("e2e_entropy_" + runId);
        Path hexFile = Path.of("e2e_entropy_" + runId + ".hex");
        Path hexDecodedFile = Path.of("e2e_entropy_" + runId + ".decoded");
        filesToCleanUp.addAll(List.of(entropyFile, hexFile, hexDecodedFile));

        Files.write(entropyFile, sourceEntropy);

        HexCommand hexCommand = main.getHandler().getCommand(HexCommand.class).orElseThrow();
        CommandResult encodeResult = hexCommand.run(new ParamsContainer("encode", entropyFile.toString(), hexFile.toString()));
        assertFalse(encodeResult.error(), "hex encode failed: " + encodeResult.message());

        CommandResult decodeResult = hexCommand.run(new ParamsContainer("decode", hexFile.toString(), hexDecodedFile.toString()));
        assertFalse(decodeResult.error(), "hex decode failed: " + decodeResult.message());
        assertArrayEquals(sourceEntropy, Files.readAllBytes(hexDecodedFile));

        // 4. Shamir-split the hex backup file, like `shamir split`.
        ShamirCommand shamirCommand = main.getHandler().getCommand(ShamirCommand.class).orElseThrow();
        CommandResult splitResult = shamirCommand.run(new ParamsContainer(
                "split", String.valueOf(allParts), String.valueOf(forRecover), hexFile.toString()
        ));
        assertFalse(splitResult.error(), "shamir split failed: " + splitResult.message());

        String partsBaseName = hexFile.toFile().getName().replaceFirst("[.][^.]+$", "");
        List<Path> partFiles = IntStream.rangeClosed(1, allParts)
                .mapToObj(i -> Path.of(partsBaseName + ".shp-" + i))
                .toList();
        filesToCleanUp.addAll(partFiles);

        byte[] hexFileContent = Files.readAllBytes(hexFile);

        // 5. Join from several different random subsets of `forRecover` parts out of `allParts`,
        // like `shamir join` with whichever parts a user actually still has.
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Integer> indices = new ArrayList<>(IntStream.rangeClosed(1, allParts).boxed().toList());
            Collections.shuffle(indices, RANDOM);
            Set<Integer> chosen = new HashSet<>(indices.subList(0, forRecover));

            List<String> joinArgs = new ArrayList<>();
            joinArgs.add("join");

            Path joinedFile = Path.of("e2e_joined_" + runId + "_" + attempt);
            filesToCleanUp.add(joinedFile);
            joinArgs.add(joinedFile.toString());

            for (int i = 1; i <= allParts; i++)
                joinArgs.add(chosen.contains(i) ? (partsBaseName + ".shp-" + i) : "null");

            CommandResult joinResult = shamirCommand.run(new ParamsContainer(joinArgs));
            assertFalse(joinResult.error(), "shamir join failed for parts " + chosen + ": " + joinResult.message());

            byte[] joinedContent = Files.readAllBytes(joinedFile);
            assertArrayEquals(hexFileContent, joinedContent, "recovered hex backup does not match the original for parts " + chosen);

            // 6. hex-decode the recovered backup and turn it into a mnemonic again - full circle.
            Path recoveredEntropyFile = Path.of("e2e_recovered_entropy_" + runId + "_" + attempt);
            filesToCleanUp.add(recoveredEntropyFile);

            CommandResult recoverDecodeResult = hexCommand.run(new ParamsContainer(
                    "decode", joinedFile.toString(), recoveredEntropyFile.toString()
            ));
            assertFalse(recoverDecodeResult.error(), "hex decode of recovered backup failed: " + recoverDecodeResult.message());

            byte[] recoveredEntropy = Files.readAllBytes(recoveredEntropyFile);
            assertArrayEquals(sourceEntropy, recoveredEntropy, "recovered entropy does not match the original for parts " + chosen);

            CommandResult recoveredSeedResult = seedCommand.run(recoveredEntropy);
            assertFalse(recoveredSeedResult.error());
            assertEquals(mnemonic, extractMnemonicWords(recoveredSeedResult.message(), expectedWords));
        }
    }

    private static List<String> extractMnemonicWords(String message, int expectedCount) {
        // The response has both an inline "Seed phrase" panel and a numbered-list panel, and
        // whenever entropy is >= 32 bytes both panels carry a 12-word AND a 24-word section, so
        // scanning for the first `expectedCount` numbered lines in the whole message would bleed
        // words in from the wrong section/panel. The numbered-list panel is rendered last and is
        // the only one with "N. word" formatted lines, so anchor on the *last* occurrence of this
        // section's own header to land inside it, past any earlier same-named section.
        String marker = expectedCount + "-word seed:";
        int markerIndex = message.lastIndexOf(marker);
        String scanFrom = markerIndex >= 0 ? message.substring(markerIndex + marker.length()) : message;

        List<String> words = new ArrayList<>();

        for (String line : scanFrom.split("\n")) {
            Matcher matcher = NUMBERED_WORD_LINE.matcher(line.trim());

            if (matcher.matches()) {
                words.add(matcher.group(1));

                if (words.size() == expectedCount)
                    break;
            }
        }

        return words;
    }

    private static byte[] extractBase64Entropy(String message) {
        return Arrays.stream(message.split("\n"))
                .filter(line -> line.startsWith("Base64 encoded: "))
                .map(line -> line.substring("Base64 encoded: ".length()))
                .findFirst()
                .map(Base64.getDecoder()::decode)
                .orElseThrow(() -> new AssertionError("Base64 encoded entropy not found in: " + message));
    }
}
