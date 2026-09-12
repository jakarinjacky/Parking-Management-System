package service;

import domain.enums.MembershipType;
import domain.enums.PaymentMethod;
import domain.enums.TicketStatus;
import domain.enums.VehicleType;
import domain.factory.VehicleFactory;
import domain.model.*;
import domain.observer.DisplayBoard;
import domain.payment.CashPayment;
import domain.payment.CreditCardPayment;
import domain.payment.Payment;
import domain.payment.PromptPayPayment;
import domain.strategy.PricingStrategy;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import repository.MembershipRepository;
import repository.PaymentRepository;
import repository.ReservationRepository;
import repository.TicketRepository;

/**
 * Service Layer / Facade: ParkingService
 * ควบคุม Business Workflows ทั้งหมดของระบบที่จอดรถ
 * เชื่อมโยง Domain Model, Repositories, Observer, และ Pricing Strategies
 */
public class ParkingService {
    private static final double LOST_TICKET_PENALTY = 300.0;
    private final ParkingLot parkingLot;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final DisplayBoard displayBoard;
    private final ReservationRepository reservationRepository;
    private final MembershipRepository membershipRepository;
    private final AtomicLong ticketSequence = new AtomicLong(1000);
    private final AtomicLong paymentSequence = new AtomicLong(5000);

    // เวลาจำลองสำหรับระบบ (Simulation Clock) ช่วยให้ทดสอบการคิดเงินตามช่วงเวลาได้ทันที
    private LocalDateTime simulatedTime;

    private final domain.ai.AIParkingService aiParkingService;

    public ParkingService(ParkingLot parkingLot,
                          TicketRepository ticketRepository,
                          PaymentRepository paymentRepository,
                          DisplayBoard displayBoard) {
        this(parkingLot, ticketRepository, paymentRepository, displayBoard,
            new ReservationRepository(), new MembershipRepository());
        }

        public ParkingService(ParkingLot parkingLot,
                  TicketRepository ticketRepository,
                  PaymentRepository paymentRepository,
                  DisplayBoard displayBoard,
                  ReservationRepository reservationRepository,
                  MembershipRepository membershipRepository) {
        this.parkingLot = parkingLot;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.displayBoard = displayBoard;
        this.reservationRepository = reservationRepository;
        this.membershipRepository = membershipRepository;
        this.aiParkingService = new domain.ai.AIParkingService();
        this.simulatedTime = LocalDateTime.now();

        this.parkingLot.registerObserver(displayBoard);
    }

    /**
     * ดึงเวลาจำลองปัจจุบันของระบบ
     * @return เวลาจำลอง LocalDateTime
     */
    public synchronized LocalDateTime getCurrentTime() {
        return simulatedTime;
    }

    /**
     * เลื่อนเวลาจำลองไปข้างหน้า (หรือย้อนหลัง) ตามจำนวนนาทีที่กำหนด
     * @param minutes จำนวนนาทีที่ต้องการปรับเลื่อน
     */
    public synchronized void fastForwardMinutes(long minutes) {
        this.simulatedTime = this.simulatedTime.plusMinutes(minutes);
    }

    /**
     * รีเซ็ตเวลาจำลองกลับมาเป็นเวลาปัจจุบันของเครื่องคอมพิวเตอร์
     */
    public synchronized void resetTime() {
        this.simulatedTime = LocalDateTime.now();
    }

    /**
     * Workflow 1: นำรถเข้าจอด (Vehicle Entry & Ticket Issuance)
     */
    public synchronized Map<String, Object> checkIn(VehicleType type, String licensePlate, boolean requiresCharging) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("กรุณาระบุเลขทะเบียนรถ");
        }

        // ตรวจสอบว่ามีรถทะเบียนนี้จอดอยู่แล้วหรือไม่
        Optional<Ticket> activeTicket = ticketRepository.findActiveByLicensePlate(licensePlate);
        if (activeTicket.isPresent()) {
            throw new IllegalStateException("ทะเบียน " + licensePlate + " มีตั๋วจอดที่ยังไม่เสร็จสิ้นอยู่ในระบบ (ช่อง " + activeTicket.get().getSlotNumber() + ")");
        }

        Optional<Reservation> reservation = reservationRepository.findActiveByPlate(licensePlate)
                .filter(r -> r.isActiveAt(simulatedTime));
        if (reservation.isPresent()) {
            if (reservation.get().getVehicleType() != type) {
                throw new IllegalArgumentException("ประเภทรถไม่ตรงกับการจองล่วงหน้า");
            }
            requiresCharging = reservation.get().isRequiresCharging();
        }

        Optional<Membership> activeMember = membershipRepository.findValidByPlate(licensePlate, simulatedTime.toLocalDate());
        if (activeMember.isPresent() && activeMember.get().getMembershipType() == MembershipType.EV_MEMBER) {
            if (type != VehicleType.ELECTRIC_VEHICLE) {
                throw new IllegalArgumentException("สมาชิก EV ต้องใช้ทะเบียนรถยนต์ไฟฟ้า");
            }
            requiresCharging = true;
        }

        // 1. Factory Pattern: สร้าง Vehicle instance
        Vehicle vehicle = VehicleFactory.createVehicle(type, licensePlate, requiresCharging);

        // 2. Aggregate Root: ค้นหาและจองช่องจอด
        Slot slot = activeMember.isPresent() && activeMember.get().getMembershipType() == MembershipType.VIP_MEMBER
            ? parkingLot.parkVehicle(vehicle, domain.enums.SlotType.VIP)
            : parkingLot.parkVehicle(vehicle);

        // 3. สร้างตั๋ว Ticket
        String ticketId = "TKT-" + simulatedTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + ticketSequence.incrementAndGet();
        Ticket ticket = new Ticket(
                ticketId,
                vehicle.getLicensePlate(),
                vehicle.getType(),
                slot.getFloorNumber(),
                slot.getSlotNumber(),
                this.simulatedTime
        );
        ticketRepository.save(ticket);
        reservation.ifPresent(r -> {
            r.markCheckedIn();
            reservationRepository.save(r);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("ticketId", ticket.getTicketId());
        result.put("licensePlate", ticket.getLicensePlate());
        result.put("vehicleType", ticket.getVehicleType().name());
        result.put("vehicleTypeDisplay", ticket.getVehicleType().getDisplayName());
        result.put("floorNumber", ticket.getFloorNumber());
        result.put("slotNumber", ticket.getSlotNumber());
        result.put("entryTime", ticket.getEntryTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("pricingStrategy", vehicle.getPricingStrategy().getStrategyName());
        result.put("rateDescription", vehicle.getPricingStrategy().getRateDescription());
        Optional<Membership> member = membershipRepository.findValidByPlate(ticket.getLicensePlate(), simulatedTime.toLocalDate());
        result.put("reservationId", reservation.map(Reservation::getReservationId).orElse(null));
        result.put("member", member.isPresent());
        result.put("memberVerified", member.isPresent());
        result.put("membershipStatus", member.isPresent() ? "ACTIVE" : "NONE");
        result.put("memberId", member.map(Membership::getMemberId).orElse(null));
        result.put("memberName", member.map(Membership::getMemberName).orElse(null));
        result.put("membershipType", member.map(m -> m.getMembershipType().name()).orElse(null));
        result.put("membershipTypeDisplay", member.map(m -> m.getMembershipType().getDisplayName()).orElse(null));
        result.put("membershipValidUntil", member.map(m -> m.getValidUntil().toString()).orElse(null));
        return result;
    }

    /**
     * Workflow 2: คำนวณค่าจอดก่อนชำระเงิน (Fee Calculation Preview)
     */
    public synchronized Map<String, Object> calculateFee(String ticketIdOrPlate) {
        Ticket ticket = findTicket(ticketIdOrPlate);
        if (ticket.getStatus() == TicketStatus.EXITED) {
            throw new IllegalStateException("ตั๋วนี้ออกจากลานจอดไปแล้ว");
        }

        Duration duration = ticket.calculateDuration(this.simulatedTime);
        long minutes = Math.max(1, duration.toMinutes());
        long hours = (minutes + 59) / 60;

        double fee;
        double parkingFee = 0.0;
        double lostTicketPenalty = 0.0;
        String strategyName;
        String rateDescription;

        boolean member = membershipRepository.findValidByPlate(ticket.getLicensePlate(), simulatedTime.toLocalDate()).isPresent();
        if (ticket.getStatus() == TicketStatus.LOST) {
            parkingFee = calculateParkingFee(ticket, duration);
            lostTicketPenalty = LOST_TICKET_PENALTY;
            fee = LOST_TICKET_PENALTY + parkingFee;
            strategyName = "Lost Ticket Penalty + Parking Fee";
            rateDescription = "ค่าจอดตามเวลาจริง " + parkingFee + " บาท + ค่าปรับตั๋วหาย " + LOST_TICKET_PENALTY + " บาท";
        } else if (member) {
            fee = 0.0;
            strategyName = "Monthly Membership";
            rateDescription = "สมาชิกแบบรายเดือน ไม่คิดค่าจอดตามชั่วโมง";
        } else {
            // ค้นหารถในช่องจอดเพื่อเรียก Strategy
            Optional<Slot> slotOpt = parkingLot.findSlot(ticket.getSlotNumber());
            Vehicle vehicle = slotOpt.flatMap(s -> Optional.ofNullable(s.getCurrentVehicle()))
                    .orElseGet(() -> VehicleFactory.createVehicle(ticket.getVehicleType(), ticket.getLicensePlate(), false));

            PricingStrategy strategy = vehicle.getPricingStrategy();
            fee = strategy.calculateFee(duration, vehicle);
            parkingFee = fee;
            strategyName = strategy.getStrategyName();
            rateDescription = strategy.getRateDescription();
        }

        Map<String, Object> preview = new HashMap<>();
        preview.put("ticketId", ticket.getTicketId());
        preview.put("licensePlate", ticket.getLicensePlate());
        preview.put("vehicleType", ticket.getVehicleType().name());
        preview.put("vehicleTypeDisplay", ticket.getVehicleType().getDisplayName());
        preview.put("slotNumber", ticket.getSlotNumber());
        preview.put("floorNumber", ticket.getFloorNumber());
        preview.put("entryTime", ticket.getEntryTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        preview.put("currentTime", this.simulatedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        preview.put("durationMinutes", minutes);
        preview.put("durationHours", hours);
        preview.put("durationDisplay", formatDuration(minutes));
        preview.put("fee", fee);
        preview.put("parkingFee", parkingFee);
        preview.put("lostTicketPenalty", lostTicketPenalty);
        preview.put("isLostTicket", ticket.getStatus() == TicketStatus.LOST);
        preview.put("status", ticket.getStatus().name());
        preview.put("strategyName", strategyName);
        preview.put("rateDescription", rateDescription);
        preview.put("member", member);
        return preview;
    }

    /**
     * Workflow 3: ชำระเงิน (Polymorphic Payment Processing)
     */
    public synchronized Map<String, Object> processPayment(String ticketId,
                                                          PaymentMethod method,
                                                          Double cashTendered,
                                                          String cardNumber,
                                                          String cardHolder) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบตั๋วหมายเลข " + ticketId));

        if (ticket.getStatus() == TicketStatus.PAID) {
            throw new IllegalStateException("ตั๋วนี้ชำระเงินเรียบร้อยแล้ว กรุณานำรถออกจากลานจอด");
        }
        if (ticket.getStatus() == TicketStatus.EXITED) {
            throw new IllegalStateException("ตั๋วนี้ถูกใช้งานออกจากระบบแล้ว");
        }

        double serverAmount = ((Number) calculateFee(ticketId).get("fee")).doubleValue();

        String paymentId = "PAY-" + paymentSequence.incrementAndGet();
        Payment payment;

        // Polymorphism: สร้างอินสแตนซ์ Payment ตามประเภทย่อย
        switch (method) {
            case PROMPTPAY -> payment = new PromptPayPayment(paymentId, ticket.getTicketId(), serverAmount, "0812345678");
            case CREDIT_CARD -> payment = new CreditCardPayment(paymentId, ticket.getTicketId(), serverAmount,
                        cardNumber != null ? cardNumber : "4111222233334444",
                        cardHolder != null ? cardHolder : "VALUED CUSTOMER");
            case CASH -> {
                double tendered = (cashTendered != null && cashTendered >= serverAmount) ? cashTendered : serverAmount;
                payment = new CashPayment(paymentId, ticket.getTicketId(), serverAmount, tendered);
            }
            default -> throw new IllegalArgumentException("ช่องทางการชำระเงินไม่ถูกต้อง");
        }

        boolean success = payment.processPayment();
        if (!success) {
            throw new IllegalStateException("การชำระเงินไม่สำเร็จ ยอดเงินไม่เพียงพอหรือถูกปฏิเสธ");
        }

        paymentRepository.save(payment);
        ticket.markPaid(serverAmount, payment.getPaymentId(), this.simulatedTime);
        ticketRepository.save(ticket);

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("paymentId", payment.getPaymentId());
        receipt.put("ticketId", ticket.getTicketId());
        receipt.put("licensePlate", ticket.getLicensePlate());
        receipt.put("slotNumber", ticket.getSlotNumber());
        receipt.put("amount", payment.getAmount());
        receipt.put("method", payment.getMethod().name());
        receipt.put("methodLabel", payment.getMethod().getLabel());
        receipt.put("transactionRef", payment.getTransactionRef());
        receipt.put("paymentTime", payment.getPaymentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (payment instanceof CashPayment cashPayment) {
            receipt.put("cashTendered", cashPayment.getCashTendered());
            receipt.put("change", cashPayment.getChange());
        }
        return receipt;
    }

    /**
     * Workflow 4: นำรถออกจากลานจอด (Exit Gate Processing)
     */
    public synchronized Map<String, Object> exitGate(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบตั๋วหมายเลข " + ticketId));

        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new IllegalStateException("สถานะตั๋วคือ " + ticket.getStatus().name() + " ยังไม่ได้ชำระเงิน ไม่สามารถเปิดไม้กั้นได้");
        }

        // ปลดปล่อยช่องจอด
        parkingLot.vacateSlot(ticket.getSlotNumber());
        ticket.markExited(this.simulatedTime);
        ticketRepository.save(ticket);

        Map<String, Object> result = new HashMap<>();
        result.put("ticketId", ticket.getTicketId());
        result.put("licensePlate", ticket.getLicensePlate());
        result.put("slotNumber", ticket.getSlotNumber());
        result.put("exitTime", ticket.getExitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("message", "ไม้กั้นเปิดแล้ว ขอให้เดินทางโดยสวัสดิภาพ");
        return result;
    }

    /**
     * Workflow 5: แจ้งตั๋วสูญหาย
     */
    public synchronized Map<String, Object> markTicketLost(String ticketIdOrPlate) {
        Ticket ticket = findTicket(ticketIdOrPlate);
        double parkingFee = calculateParkingFee(ticket, ticket.calculateDuration(this.simulatedTime));
        ticket.markLost(LOST_TICKET_PENALTY + parkingFee);
        ticketRepository.save(ticket);
        return calculateFee(ticket.getTicketId());
    }

    private double calculateParkingFee(Ticket ticket, Duration duration) {
        if (membershipRepository.findValidByPlate(ticket.getLicensePlate(), simulatedTime.toLocalDate()).isPresent()) {
            return 0.0;
        }

        Optional<Slot> slotOpt = parkingLot.findSlot(ticket.getSlotNumber());
        Vehicle vehicle = slotOpt.flatMap(s -> Optional.ofNullable(s.getCurrentVehicle()))
                .orElseGet(() -> VehicleFactory.createVehicle(ticket.getVehicleType(), ticket.getLicensePlate(), false));
        return vehicle.getPricingStrategy().calculateFee(duration, vehicle);
    }

    /**
     * ค้นหาตั๋วจอดรถ โดยสามารถระบุได้ทั้งรหัสตั๋ว (Ticket ID) หรือป้ายทะเบียนรถ (License Plate)
     * @param ticketIdOrPlate รหัสตั๋วหรือป้ายทะเบียนรถ
     * @return อ็อบเจกต์ Ticket ที่ค้นพบ
     * @throws IllegalArgumentException หากไม่พบข้อมูลตั๋วในระบบ
     */
    public Ticket findTicket(String ticketIdOrPlate) {
        if (ticketIdOrPlate == null || ticketIdOrPlate.trim().isEmpty()) {
            throw new IllegalArgumentException("กรุณาระบุเลขที่ตั๋วหรือป้ายทะเบียน");
        }
        String clean = ticketIdOrPlate.trim();
        Optional<Ticket> byId = ticketRepository.findById(clean);
        if (byId.isPresent()) return byId.get();

        Optional<Ticket> byPlate = ticketRepository.findActiveByLicensePlate(clean);
        if (byPlate.isPresent()) return byPlate.get();

        throw new IllegalArgumentException("ไม่พบข้อมูลตั๋วสำหรับ: " + clean);
    }

    /**
     * ดึงอ็อบเจกต์ ParkingLot ของระบบ
     */
    public ParkingLot getParkingLot() {
        return parkingLot;
    }

    /**
     * ดึง TicketRepository ที่เก็บข้อมูลตั๋วทั้งหมด
     */
    public TicketRepository getTicketRepository() {
        return ticketRepository;
    }

    /**
     * ดึง PaymentRepository ที่เก็บประวัติการชำระเงินทั้งหมด
     */
    public PaymentRepository getPaymentRepository() {
        return paymentRepository;
    }

    public ReservationRepository getReservationRepository() { return reservationRepository; }
    public MembershipRepository getMembershipRepository() { return membershipRepository; }

    public synchronized Map<String, Object> createReservation(String licensePlate, VehicleType type,
                                                               boolean requiresCharging, LocalDateTime startTime,
                                                               LocalDateTime endTime) {
        if (reservationRepository.findActiveByPlate(licensePlate).isPresent()) {
            throw new IllegalStateException("ทะเบียนนี้มีการจองที่ยังใช้งานอยู่แล้ว");
        }
        String id = "RSV-" + (reservationRepository.findAll().size() + 1);
        Reservation reservation = new Reservation(id, licensePlate, type, requiresCharging, startTime, endTime);
        reservationRepository.save(reservation);
        return reservationMap(reservation);
    }

    public synchronized Map<String, Object> cancelReservation(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการจอง " + reservationId));
        reservation.cancel();
        reservationRepository.save(reservation);
        return reservationMap(reservation);
    }

    public synchronized Map<String, Object> createMembership(String memberId, String memberName,
                                                              String licensePlate, MembershipType membershipType,
                                                              LocalDate validFrom,
                                                              LocalDate validUntil) {
        Membership membership = new Membership(memberId, memberName, licensePlate, membershipType, validFrom, validUntil);
        membershipRepository.save(membership);
        Map<String, Object> result = new HashMap<>();
        result.put("memberId", membership.getMemberId());
        result.put("memberName", membership.getMemberName());
        result.put("licensePlate", membership.getLicensePlate());
        result.put("membershipType", membership.getMembershipType().name());
        result.put("membershipTypeDisplay", membership.getMembershipType().getDisplayName());
        result.put("validFrom", membership.getValidFrom().toString());
        result.put("validUntil", membership.getValidUntil().toString());
        return result;
    }

    public synchronized Map<String, Object> getDailyDashboard(LocalDate date) {
        double revenue = paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentTime().toLocalDate().equals(date))
                .mapToDouble(p -> p.getAmount()).sum();
        long paidTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == TicketStatus.PAID || t.getStatus() == TicketStatus.EXITED)
                .count();
        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("revenue", revenue);
        result.put("paidTickets", paidTickets);
        result.put("occupancyRate", parkingLot.getTotalCapacity() == 0 ? 0 :
                Math.round((double) parkingLot.getTotalOccupied() * 100 / parkingLot.getTotalCapacity()));
        result.put("totalCapacity", parkingLot.getTotalCapacity());
        result.put("occupiedSlots", parkingLot.getTotalOccupied());
        result.put("availableSlots", parkingLot.getTotalAvailable());
        result.put("reservationCount", reservationRepository.findAll().stream()
                .filter(r -> r.getStartTime().toLocalDate().equals(date)).count());
        result.put("activeMembers", membershipRepository.findAll().stream().filter(m -> m.isValidOn(date)).count());
        return result;
    }

    private Map<String, Object> reservationMap(Reservation reservation) {
        Map<String, Object> result = new HashMap<>();
        result.put("reservationId", reservation.getReservationId());
        result.put("licensePlate", reservation.getLicensePlate());
        result.put("vehicleType", reservation.getVehicleType().name());
        result.put("requiresCharging", reservation.isRequiresCharging());
        result.put("startTime", reservation.getStartTime().toString());
        result.put("endTime", reservation.getEndTime().toString());
        result.put("cancelled", reservation.isCancelled());
        result.put("checkedIn", reservation.isCheckedIn());
        return result;
    }

    /**
     * ดึง DisplayBoard ป้ายแสดงผลสถานะที่เชื่อมต่อผ่าน Observer Pattern
     */
    public DisplayBoard getDisplayBoard() {
        return displayBoard;
    }

    /**
     * ดึง AIParkingService สำหรับการประมวลผลระบบปัญญาประดิษฐ์และ XAI
     */
    public domain.ai.AIParkingService getAIParkingService() {
        return aiParkingService;
    }

    /**
     * แปลงจำนวนนาทีให้อยู่ในรูปแบบข้อความที่อ่านง่าย (เช่น "1 วัน 2 ชม. 30 นาที")
     * @param minutes จำนวนนาทีทั้งหมด
     * @return ข้อความแสดงระยะเวลา
     */
    private String formatDuration(long minutes) {
        long d = minutes / (24 * 60);
        long h = (minutes % (24 * 60)) / 60;
        long m = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" วัน ");
        if (h > 0 || d > 0) sb.append(h).append(" ชม. ");
        sb.append(m).append(" นาที");
        return sb.toString();
    }
}
