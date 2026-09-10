package service;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
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
import repository.PaymentRepository;
import repository.TicketRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service Layer / Facade: ParkingService
 * ควบคุม Business Workflows ทั้งหมดของระบบที่จอดรถ
 * เชื่อมโยง Domain Model, Repositories, Observer, และ Pricing Strategies
 */
public class ParkingService {
    private final ParkingLot parkingLot;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final DisplayBoard displayBoard;
    private final AtomicLong ticketSequence = new AtomicLong(1000);
    private final AtomicLong paymentSequence = new AtomicLong(5000);

    // เวลาจำลองสำหรับระบบ (Simulation Clock) ช่วยให้ทดสอบการคิดเงินตามช่วงเวลาได้ทันที
    private LocalDateTime simulatedTime;

    private final domain.ai.AIParkingService aiParkingService;

    public ParkingService(ParkingLot parkingLot,
                          TicketRepository ticketRepository,
                          PaymentRepository paymentRepository,
                          DisplayBoard displayBoard) {
        this.parkingLot = parkingLot;
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.displayBoard = displayBoard;
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

        // 1. Factory Pattern: สร้าง Vehicle instance
        Vehicle vehicle = VehicleFactory.createVehicle(type, licensePlate, requiresCharging);

        // 2. Aggregate Root: ค้นหาและจองช่องจอด
        Slot slot = parkingLot.parkVehicle(vehicle);

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
        String strategyName;
        String rateDescription;

        if (ticket.getStatus() == TicketStatus.LOST) {
            fee = 300.0; // ค่าปรับกรณีตั๋วสูญหาย
            strategyName = "Lost Ticket Penalty";
            rateDescription = "ค่าธรรมเนียมปรับตั๋วสูญหาย อัตราเหมาจ่าย 300 บาท";
        } else {
            // ค้นหารถในช่องจอดเพื่อเรียก Strategy
            Optional<Slot> slotOpt = parkingLot.findSlot(ticket.getSlotNumber());
            Vehicle vehicle = slotOpt.flatMap(s -> Optional.ofNullable(s.getCurrentVehicle()))
                    .orElseGet(() -> VehicleFactory.createVehicle(ticket.getVehicleType(), ticket.getLicensePlate(), false));

            PricingStrategy strategy = vehicle.getPricingStrategy();
            fee = strategy.calculateFee(duration, vehicle);
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
        preview.put("status", ticket.getStatus().name());
        preview.put("strategyName", strategyName);
        preview.put("rateDescription", rateDescription);
        return preview;
    }

    /**
     * Workflow 3: ชำระเงิน (Polymorphic Payment Processing)
     */
    public synchronized Map<String, Object> processPayment(String ticketId,
                                                          PaymentMethod method,
                                                          double amount,
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

        String paymentId = "PAY-" + paymentSequence.incrementAndGet();
        Payment payment;

        // Polymorphism: สร้างอินสแตนซ์ Payment ตามประเภทย่อย
        switch (method) {
            case PROMPTPAY:
                payment = new PromptPayPayment(paymentId, ticket.getTicketId(), amount, "0812345678");
                break;
            case CREDIT_CARD:
                payment = new CreditCardPayment(paymentId, ticket.getTicketId(), amount,
                        cardNumber != null ? cardNumber : "4111222233334444",
                        cardHolder != null ? cardHolder : "VALUED CUSTOMER");
                break;
            case CASH:
                double tendered = (cashTendered != null && cashTendered >= amount) ? cashTendered : amount;
                payment = new CashPayment(paymentId, ticket.getTicketId(), amount, tendered);
                break;
            default:
                throw new IllegalArgumentException("ช่องทางการชำระเงินไม่ถูกต้อง");
        }

        boolean success = payment.processPayment();
        if (!success) {
            throw new IllegalStateException("การชำระเงินไม่สำเร็จ ยอดเงินไม่เพียงพอหรือถูกปฏิเสธ");
        }

        paymentRepository.save(payment);
        ticket.markPaid(amount, payment.getPaymentId(), this.simulatedTime);
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
        if (payment instanceof CashPayment) {
            receipt.put("cashTendered", ((CashPayment) payment).getCashTendered());
            receipt.put("change", ((CashPayment) payment).getChange());
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
        ticket.markLost(300.0);
        ticketRepository.save(ticket);
        return calculateFee(ticket.getTicketId());
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
