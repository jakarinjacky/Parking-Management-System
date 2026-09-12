package repository;

import domain.enums.VehicleType;
import domain.model.Reservation;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import util.FileStorage;

public class ReservationRepository {
    private final Map<String, Reservation> storage = new ConcurrentHashMap<>();
    private final Path storagePath;

    public ReservationRepository() { this(null); }

    public ReservationRepository(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    public synchronized void save(Reservation reservation) {
        storage.put(reservation.getReservationId(), reservation);
        persist();
    }
    public Optional<Reservation> findById(String id) { return id == null ? Optional.empty() : Optional.ofNullable(storage.get(id.trim().toUpperCase())); }
    public Optional<Reservation> findActiveByPlate(String plate) {
        if (plate == null) return Optional.empty();
        String target = plate.trim().toUpperCase();
        return storage.values().stream().filter(r -> r.getLicensePlate().equals(target) && !r.isCancelled() && !r.isCheckedIn()).findFirst();
    }
    public List<Reservation> findAll() { return storage.values().stream().sorted(Comparator.comparing(Reservation::getStartTime)).collect(Collectors.toList()); }

    private void load() {
        if (storagePath == null) return;
        for (String line : FileStorage.readLines(storagePath)) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 8) continue;
            Reservation reservation = new Reservation(
                    FileStorage.decode(fields[0]), FileStorage.decode(fields[1]),
                    VehicleType.valueOf(fields[2]), Boolean.parseBoolean(fields[3]),
                    java.time.LocalDateTime.parse(fields[4]), java.time.LocalDateTime.parse(fields[5]));
            if (Boolean.parseBoolean(fields[6])) reservation.cancel();
            if (Boolean.parseBoolean(fields[7])) reservation.markCheckedIn();
            storage.put(reservation.getReservationId(), reservation);
        }
    }

    private void persist() {
        if (storagePath == null) return;
        List<String> lines = storage.values().stream().map(r -> String.join("|",
                FileStorage.encode(r.getReservationId()), FileStorage.encode(r.getLicensePlate()),
                r.getVehicleType().name(), Boolean.toString(r.isRequiresCharging()),
                r.getStartTime().toString(), r.getEndTime().toString(),
                Boolean.toString(r.isCancelled()), Boolean.toString(r.isCheckedIn()))).toList();
        FileStorage.writeLines(storagePath, lines);
    }
}
