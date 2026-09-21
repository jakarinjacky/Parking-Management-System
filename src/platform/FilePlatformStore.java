package platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Atomic local-file store used for development and the offline demo. */
public final class FilePlatformStore implements PlatformStore {
    private final Path file;

    public FilePlatformStore(Path file) { this.file = file; }

    @Override public String load() throws Exception {
        return Files.exists(file) ? Files.readString(file) : null;
    }

    @Override public void save(String json) throws Exception {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "platform-", ".tmp");
        try {
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    @Override public String description() { return "atomic-json-file"; }
}
