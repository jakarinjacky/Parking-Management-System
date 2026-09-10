package repository;

import domain.enums.TicketStatus;
import domain.model.Ticket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TicketRepository {
    private final Map<String, Ticket> ticketStorage = new ConcurrentHashMap<>();

    public synchronized void save(Ticket ticket) {
        ticketStorage.put(ticket.getTicketId(), ticket);
    }

    public Optional<Ticket> findById(String ticketId) {
        if (ticketId == null) return Optional.empty();
        return Optional.ofNullable(ticketStorage.get(ticketId.trim().toUpperCase()));
    }

    public Optional<Ticket> findActiveByLicensePlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String target = licensePlate.trim().toUpperCase();
        return ticketStorage.values().stream()
                .filter(t -> t.getLicensePlate().equalsIgnoreCase(target) &&
                        (t.getStatus() == TicketStatus.ACTIVE || t.getStatus() == TicketStatus.PAID))
                .findFirst();
    }

    public List<Ticket> findActiveTickets() {
        return ticketStorage.values().stream()
                .filter(t -> t.getStatus() == TicketStatus.ACTIVE)
                .sorted(Comparator.comparing(Ticket::getEntryTime).reversed())
                .collect(Collectors.toList());
    }

    public List<Ticket> findAll() {
        return ticketStorage.values().stream()
                .sorted(Comparator.comparing(Ticket::getEntryTime).reversed())
                .collect(Collectors.toList());
    }
}
