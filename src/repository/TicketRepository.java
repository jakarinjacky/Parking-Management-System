package repository;

import domain.enums.TicketStatus;
import domain.model.Ticket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class TicketRepository {
    private final Map<String, Ticket> ticketStorage = new ConcurrentHashMap<>();

    // [OOP: METHOD] Method save() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void save(Ticket ticket) {
        ticketStorage.put(ticket.getTicketId(), ticket);
    }

    // [OOP: METHOD] Method findById() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Ticket> findById(String ticketId) {
        if (ticketId == null) return Optional.empty();
        return Optional.ofNullable(ticketStorage.get(ticketId.trim().toUpperCase()));
    }

    // [OOP: METHOD] Method findActiveByLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Ticket> findActiveByLicensePlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String target = licensePlate.trim().toUpperCase();
        return ticketStorage.values().stream()
                .filter(t -> t.getLicensePlate().equalsIgnoreCase(target) &&
                        (t.getStatus() == TicketStatus.ACTIVE || t.getStatus() == TicketStatus.PAID))
                .findFirst();
    }

    // [OOP: METHOD] Method findActiveTickets() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<Ticket> findActiveTickets() {
        return ticketStorage.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE)
                .sorted(Comparator.comparing(Ticket::getEntryTime).reversed())
                .collect(Collectors.toList());
    }

    // [OOP: METHOD] Method findAll() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<Ticket> findAll() {
        return ticketStorage.values().stream()
                .sorted(Comparator.comparing(Ticket::getEntryTime).reversed())
                .collect(Collectors.toList());
    }
}
