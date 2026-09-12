package repository;

import domain.enums.MembershipType;
import domain.model.Membership;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.FileStorage;

public class MembershipRepository {
    private final Map<String, Membership> storage = new ConcurrentHashMap<>();
    private final Path storagePath;

    public MembershipRepository() { this(null); }

    public MembershipRepository(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    public synchronized void save(Membership membership) {
        storage.put(membership.getMemberId(), membership);
        persist();
    }
    public Optional<Membership> findValidByPlate(String plate, LocalDate date) {
        if (plate == null) return Optional.empty();
        String target = plate.trim().toUpperCase();
        return storage.values().stream().filter(m -> m.getLicensePlate().equals(target) && m.isValidOn(date)).findFirst();
    }
    public List<Membership> findAll() { return new ArrayList<>(storage.values()); }

    private void load() {
        if (storagePath == null) return;
        for (String line : FileStorage.readLines(storagePath)) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 6) continue;
            Membership membership = new Membership(
                    FileStorage.decode(fields[0]), FileStorage.decode(fields[1]), FileStorage.decode(fields[2]),
                    MembershipType.valueOf(fields[3]), LocalDate.parse(fields[4]), LocalDate.parse(fields[5]));
            storage.put(membership.getMemberId(), membership);
        }
    }

    private void persist() {
        if (storagePath == null) return;
        List<String> lines = storage.values().stream().map(m -> String.join("|",
                FileStorage.encode(m.getMemberId()), FileStorage.encode(m.getMemberName()),
                FileStorage.encode(m.getLicensePlate()), m.getMembershipType().name(),
                m.getValidFrom().toString(), m.getValidUntil().toString())).toList();
        FileStorage.writeLines(storagePath, lines);
    }
}
