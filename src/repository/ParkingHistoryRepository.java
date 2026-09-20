package repository;

import domain.enums.VehicleType;
import domain.model.ParkingHistoryRecord;
import domain.model.Ticket;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import util.FileStorage;

/** Repository เก็บประวัติรถเข้าออกลงไฟล์ และรักษาข้อมูลย้อนหลังไม่เกิน 3 เดือน */
// [OOP: CLASS] Repository แยกงานจัดเก็บประวัติออกจาก Business Workflow
public class ParkingHistoryRepository {
    private static final int RETENTION_MONTHS = 3;
    private final Map<String, ParkingHistoryRecord> records = new LinkedHashMap<>();
    private final Path storagePath;

    public ParkingHistoryRepository() {
        this(Path.of("data", "parking-history.db"));
    }

    public ParkingHistoryRepository(Path storagePath) {
        this.storagePath = storagePath;
        load();
        purgeExpired(LocalDateTime.now());
    }

    // [OOP: METHOD] เพิ่มประวัติทันทีเมื่อรถเข้าลาน
    public synchronized void recordEntry(Ticket ticket, LocalDateTime currentTime) {
        purgeExpired(currentTime);
        records.put(ticket.getTicketId(), new ParkingHistoryRecord(
                ticket.getTicketId(), ticket.getLicensePlate(), ticket.getVehicleType(),
                ticket.getFloorNumber(), ticket.getSlotNumber(), ticket.getEntryTime()));
        persist();
    }

    // [OOP: METHOD] อัปเดตประวัติเดิมเมื่อรถออก
    public synchronized void recordExit(Ticket ticket, LocalDateTime currentTime) {
        purgeExpired(currentTime);
        ParkingHistoryRecord record = records.computeIfAbsent(ticket.getTicketId(), id ->
                new ParkingHistoryRecord(ticket.getTicketId(), ticket.getLicensePlate(), ticket.getVehicleType(),
                        ticket.getFloorNumber(), ticket.getSlotNumber(), ticket.getEntryTime()));
        record.markExited(ticket.getExitTime(), ticket.getFee());
        persist();
    }

    // [OOP: METHOD] ค้นประวัติตามช่วงวันและทะเบียนรถ
    public synchronized List<ParkingHistoryRecord> find(LocalDate from, LocalDate to,
                                                        String licensePlate, LocalDateTime currentTime) {
        purgeExpired(currentTime);
        String plate = licensePlate == null ? "" : licensePlate.trim().toUpperCase();
        LocalDate effectiveFrom = from == null ? currentTime.minusMonths(RETENTION_MONTHS).toLocalDate() : from;
        LocalDate effectiveTo = to == null ? currentTime.toLocalDate() : to;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("วันที่เริ่มต้นต้องไม่มากกว่าวันที่สิ้นสุด");
        }
        return records.values().stream()
                .filter(r -> !r.getEntryTime().toLocalDate().isBefore(effectiveFrom))
                .filter(r -> !r.getEntryTime().toLocalDate().isAfter(effectiveTo))
                .filter(r -> plate.isEmpty() || r.getLicensePlate().contains(plate))
                .sorted(Comparator.comparing(ParkingHistoryRecord::getEntryTime).reversed())
                .collect(Collectors.toList());
    }

    public int getRetentionMonths() { return RETENTION_MONTHS; }

    // [OOP: METHOD] ลบรายการที่ออกจากลานและเก่ากว่า 3 เดือนอัตโนมัติ
    public synchronized void purgeExpired(LocalDateTime currentTime) {
        LocalDateTime cutoff = currentTime.minusMonths(RETENTION_MONTHS);
        boolean changed = records.values().removeIf(r ->
                (r.hasExited() ? r.getExitTime() : r.getEntryTime()).isBefore(cutoff));
        if (changed) persist();
    }

    private void load() {
        for (String line : FileStorage.readLines(storagePath)) {
            if (line == null || line.isBlank()) continue;
            try {
                String[] p = line.split("\\|", -1);
                LocalDateTime exit = p[6].isBlank() ? null : LocalDateTime.parse(p[6]);
                ParkingHistoryRecord record = new ParkingHistoryRecord(
                        FileStorage.decode(p[0]), FileStorage.decode(p[1]), VehicleType.valueOf(p[2]),
                        Integer.parseInt(p[3]), FileStorage.decode(p[4]), LocalDateTime.parse(p[5]),
                        exit, Double.parseDouble(p[7]));
                records.put(record.getTicketId(), record);
            } catch (RuntimeException ignored) {
                System.err.println("ข้ามข้อมูลประวัติที่อ่านไม่ได้: " + line);
            }
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (ParkingHistoryRecord r : records.values()) {
            lines.add(String.join("|",
                    FileStorage.encode(r.getTicketId()),
                    FileStorage.encode(r.getLicensePlate()),
                    r.getVehicleType().name(),
                    String.valueOf(r.getFloorNumber()),
                    FileStorage.encode(r.getSlotNumber()),
                    r.getEntryTime().toString(),
                    r.getExitTime() == null ? "" : r.getExitTime().toString(),
                    String.valueOf(r.getFee())));
        }
        FileStorage.writeLines(storagePath, lines);
    }
}
