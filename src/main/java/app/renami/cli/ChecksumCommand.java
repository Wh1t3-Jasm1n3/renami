package app.renami.cli;

import app.renami.hash.HashType;
import app.renami.hash.VerificationFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "checksum", description = "Generate or verify checksum sidecar files (SFV/MD5/SHA1/SHA256/CRC32).", mixinStandardHelpOptions = true)
public class ChecksumCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT", description = "file or folder to hash (ignored with --verify)")
    Path input;

    @Option(names = "--algo", defaultValue = "sha256", description = "crc32 | md5 | sha1 | sha256 (default: ${DEFAULT-VALUE})")
    String algo;

    @Option(names = "--write", description = "write a sidecar file, e.g. checksums.sfv or checksums.sha256")
    Path write;

    @Option(names = "--verify", description = "verify against an existing sidecar file")
    Path verify;

    @Override
    public Integer call() throws Exception {
        if (verify != null) {
            return verify();
        }
        HashType type = write != null ? VerificationFile.defaultAlgorithm(write, HashType.parse(algo)) : HashType.parse(algo);
        List<VerificationFile.Entry> entries = new ArrayList<>();
        for (Path file : filesToHash(input)) {
            String relative = Files.isDirectory(input)
                    ? input.relativize(file).toString()
                    : file.getFileName().toString();
            entries.add(new VerificationFile.Entry(relative, type.hex(file)));
        }
        if (write != null) {
            VerificationFile.write(write, entries);
            System.out.println(entries.size() + " entries written to " + write);
        } else {
            for (VerificationFile.Entry entry : entries) {
                System.out.println(entry.hash() + "  " + entry.name());
            }
        }
        return 0;
    }

    private int verify() throws IOException {
        List<VerificationFile.Entry> entries = VerificationFile.read(verify);
        if (entries.isEmpty()) {
            System.err.println("error: no entries found in " + verify);
            return 2;
        }
        Path base = verify.toAbsolutePath().getParent();
        int ok = 0, mismatch = 0, missing = 0;
        for (VerificationFile.Entry entry : entries) {
            HashType type = HashType.byHexLength(entry.hash().length());
            if (type == null) {
                System.out.println("UNKNOWN  " + entry.name() + " (digest length " + entry.hash().length() + ")");
                mismatch++;
                continue;
            }
            Path file = base.resolve(entry.name()).normalize();
            if (!Files.isRegularFile(file)) {
                System.out.println("MISSING  " + entry.name());
                missing++;
                continue;
            }
            String actual = type.hex(file);
            if (actual.equals(entry.hash())) {
                System.out.println("OK       " + entry.name());
                ok++;
            } else {
                System.out.println("MISMATCH " + entry.name() + " (expected " + entry.hash() + ", got " + actual + ")");
                mismatch++;
            }
        }
        System.out.println();
        System.out.println(ok + " ok, " + mismatch + " mismatched, " + missing + " missing");
        return mismatch + missing == 0 ? 0 : 1;
    }

    private static List<Path> filesToHash(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        try (Stream<Path> stream = Files.walk(input)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
