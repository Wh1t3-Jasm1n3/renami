package app.renami.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumCommandTest {

    @TempDir
    Path dir;

    @Test
    void writeThenVerifyRoundTrip() throws Exception {
        Files.writeString(dir.resolve("a.mkv"), "alpha");
        Files.writeString(dir.resolve("b.mkv"), "beta");

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int writeCode = new CommandLine(new ChecksumCommand())
                    .execute(dir.toString(), "--write", dir.resolve("checksums.sfv").toString());
            assertEquals(0, writeCode);

            int verifyCode = new CommandLine(new ChecksumCommand())
                    .execute(dir.toString(), "--verify", dir.resolve("checksums.sfv").toString());
            assertEquals(0, verifyCode);
            String out = captured.toString(StandardCharsets.UTF_8);
            assertTrue(out.contains("OK       a.mkv"), out);
            assertTrue(out.contains("2 ok, 0 mismatched, 0 missing"), out);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void verifyReportsTamperedFile() throws Exception {
        Files.writeString(dir.resolve("a.mkv"), "alpha");
        new CommandLine(new ChecksumCommand())
                .execute(dir.toString(), "--write", dir.resolve("sums.sha256").toString());
        Files.writeString(dir.resolve("a.mkv"), "tampered");

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int code = new CommandLine(new ChecksumCommand())
                    .execute(dir.toString(), "--verify", dir.resolve("sums.sha256").toString());
            assertEquals(1, code);
            assertTrue(captured.toString(StandardCharsets.UTF_8).contains("MISMATCH a.mkv"));
        } finally {
            System.setOut(original);
        }
    }
}
