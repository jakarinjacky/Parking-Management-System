package repository;

import domain.enums.MembershipType;
import domain.model.Membership;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.FileStorage;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class MembershipRepository {
    private final Map<String, Membership> storage = new ConcurrentHashMap<>();
    private final Path storagePath;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object MembershipRepository
    public MembershipRepository() { this(null); }

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object MembershipRepository
    public MembershipRepository(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    // [OOP: METHOD] Method save() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void save(Membership membership) {
        storage.put(membership.getMemberId(), membership);
        persist();
    }
    // [OOP: METHOD] Method findValidByPlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Membership> findValidByPlate(String plate, LocalDate date) {
        if (plate == null) return Optional.empty();
        String target = plate.trim().toUpperCase();
        return storage.values().stream().filter(m -> m.getLicensePlate().equals(target) && m.isValidOn(date)).findFirst();
    }
    // [OOP: METHOD] Method findAll() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<Membership> findAll() { return new ArrayList<>(storage.values()); }

    // [OOP: METHOD] Method load() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method persist() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private void persist() {
        if (storagePath == null) return;
        List<String> lines = storage.values().stream().map(m -> String.join("|",
                FileStorage.encode(m.getMemberId()), FileStorage.encode(m.getMemberName()),
                FileStorage.encode(m.getLicensePlate()), m.getMembershipType().name(),
                m.getValidFrom().toString(), m.getValidUntil().toString())).toList();
        FileStorage.writeLines(storagePath, lines);
    }
}
