package test;

import domain.enums.MembershipType;
import domain.enums.PaymentMethod;
import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.factory.SlotFactory;
import domain.factory.VehicleFactory;
import domain.model.ParkingFloor;
import domain.model.ParkingLot;
import domain.model.Slot;
import domain.model.Vehicle;
import domain.observer.DisplayBoard;
import domain.payment.CashPayment;
import domain.payment.PromptPayPayment;
import domain.strategy.PricingStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import repository.PaymentRepository;
import repository.TicketRepository;
import service.ParkingService;

/**
 * Automated Unit Test Suite
 * ทดสอบความถูกต้องของ OOP Logic, Invariants, และ Business Rules
 */
public class ParkingSystemTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   Running Parking Management System Unit Tests  ");
        System.out.println("=================================================");

        testVehicleSlotCompatibility();
        testPricingStrategies();
        testParkingLotAndObserverWorkflow();
        testPaymentProcessing();
        testServerAuthoritativePaymentAmount();
        testReservationMembershipAndDashboard();
        testMembershipAndReservationPersistence();
        testLostTicketIncludesParkingFee();
        testAIServicesAndExplainability();

        System.out.println("\n-------------------------------------------------");
        System.out.println("Test Results: " + testsPassed + " / " + testsRun + " passed.");
        if (testsPassed == testsRun) {
            System.out.println("ALL OOP & AI UNIT TESTS PASSED SUCCESSFULLY! (100%)");
        } else {
            System.err.println("SOME TESTS FAILED!");
        }
        System.out.println("=================================================");
    }

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            System.err.println("  [FAIL] " + testName);
        }
    }

    private static void assertEquals(String testName, double expected, double actual, double delta) {
        testsRun++;
        if (Math.abs(expected - actual) <= delta) {
            testsPassed++;
            System.out.println("  [PASS] " + testName + " (Result: " + actual + ")");
        } else {
            System.err.println("  [FAIL] " + testName + " - Expected " + expected + " but got " + actual);
        }
    }

    private static void testVehicleSlotCompatibility() {
        System.out.println("\n1. Testing Vehicle & Slot Polymorphic Compatibility (canParkIn):");

        Slot carSlot = SlotFactory.createSlot("S-CAR", 1, SlotType.STANDARD);
        Slot motoSlot = SlotFactory.createSlot("S-MOTO", 1, SlotType.MOTORCYCLE);
        Slot evSlot = SlotFactory.createSlot("S-EV", 1, SlotType.EV_CHARGING);
        Slot largeSlot = SlotFactory.createSlot("S-TRUCK", 1, SlotType.LARGE);

        Vehicle car = VehicleFactory.createVehicle(VehicleType.CAR, "1กก-1111", false);
        Vehicle moto = VehicleFactory.createVehicle(VehicleType.MOTORCYCLE, "2ขข-2222", false);
        Vehicle ev = VehicleFactory.createVehicle(VehicleType.ELECTRIC_VEHICLE, "3คค-3333", true);
        Vehicle truck = VehicleFactory.createVehicle(VehicleType.TRUCK, "4งง-4444", false);

        assertTrue("Car can park in Standard slot", carSlot.canFitVehicle(car));
        assertTrue("Car can park in Large slot", largeSlot.canFitVehicle(car));
        assertTrue("Car CANNOT park in Motorcycle slot", !motoSlot.canFitVehicle(car));

        assertTrue("Motorcycle can park in Motorcycle slot", motoSlot.canFitVehicle(moto));
        assertTrue("Motorcycle CANNOT park in Standard slot", !carSlot.canFitVehicle(moto));

        assertTrue("EV with charging requirement can park in EV slot", evSlot.canFitVehicle(ev));
        assertTrue("EV with charging requirement CANNOT park in Standard slot", !carSlot.canFitVehicle(ev));

        assertTrue("Truck can park in Large slot", largeSlot.canFitVehicle(truck));
        assertTrue("Truck CANNOT park in Standard slot", !carSlot.canFitVehicle(truck));
    }

    private static void testPricingStrategies() {
        System.out.println("\n2. Testing Pricing Strategies (Strategy Pattern):");

        Vehicle car = VehicleFactory.createVehicle(VehicleType.CAR, "CAR-01", false);
        PricingStrategy standard = car.getPricingStrategy();

        // 10 นาที (grace period <= 15 min) -> ฟรี 0 บาท
        assertEquals("Car <= 15 min is Free", 0.0, standard.calculateFee(Duration.ofMinutes(10), car), 0.001);
        // 45 นาที -> ชั่วโมงแรก 20 บาท
        assertEquals("Car 45 min is 20 THB", 20.0, standard.calculateFee(Duration.ofMinutes(45), car), 0.001);
        // 2 ชั่วโมง 15 นาที -> 3 ชั่วโมง = 20 + 30 + 30 = 80 บาท
        assertEquals("Car 2h 15m is 80 THB", 80.0, standard.calculateFee(Duration.ofMinutes(135), car), 0.001);

        Vehicle moto = VehicleFactory.createVehicle(VehicleType.MOTORCYCLE, "MOTO-01", false);
        PricingStrategy motoStrategy = moto.getPricingStrategy();
        // 25 นาที (grace period <= 30 min) -> 0 บาท
        assertEquals("Motorcycle <= 30 min is Free", 0.0, motoStrategy.calculateFee(Duration.ofMinutes(25), moto), 0.001);
        // 75 นาที -> 2 ชั่วโมง = 20 บาท
        assertEquals("Motorcycle 75 min is 20 THB", 20.0, motoStrategy.calculateFee(Duration.ofMinutes(75), moto), 0.001);

        Vehicle ev = VehicleFactory.createVehicle(VehicleType.ELECTRIC_VEHICLE, "EV-01", true);
        PricingStrategy evStrategy = ev.getPricingStrategy();
        // 60 นาที -> 40 บาท
        assertEquals("EV 1 hour is 40 THB", 40.0, evStrategy.calculateFee(Duration.ofMinutes(60), ev), 0.001);
    }

    private static void testParkingLotAndObserverWorkflow() {
        System.out.println("\n3. Testing ParkingLot, Aggregate Root & Observer Pattern:");

        ParkingLot lot = new ParkingLot("Test Lot", "Bangkok");
        ParkingFloor f1 = new ParkingFloor(1, "Floor 1");
        f1.addSlot(SlotFactory.createSlot("T-01", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("T-02", 1, SlotType.STANDARD));
        lot.addFloor(f1);

        DisplayBoard board = new DisplayBoard("BOARD-01");
        lot.registerObserver(board);

        assertTrue("Initial available slots should be 2", lot.getTotalAvailable() == 2);
        assertTrue("Board shows 2 available slots", board.getTotalAvailableSlots() == 2);

        Vehicle car1 = VehicleFactory.createVehicle(VehicleType.CAR, "CAR-AAA", false);
        Slot assignedSlot = lot.parkVehicle(car1);

        assertTrue("Slot assigned successfully", assignedSlot != null && assignedSlot.getSlotNumber().equals("T-01"));
        assertTrue("Lot available decreases to 1", lot.getTotalAvailable() == 1);
        assertTrue("Board receives observer update for 1 slot", board.getTotalAvailableSlots() == 1);
        assertTrue("Slot status is now OCCUPIED", assignedSlot.getStatus() == domain.enums.SlotStatus.OCCUPIED);

        lot.vacateSlot("T-01");
        assertTrue("Lot available restored to 2", lot.getTotalAvailable() == 2);
        assertTrue("Board updated to 2 after vacate", board.getTotalAvailableSlots() == 2);
    }

    private static void testPaymentProcessing() {
        System.out.println("\n4. Testing Polymorphic Payment Processing:");

        PromptPayPayment promptPay = new PromptPayPayment("PAY-1", "TKT-1", 100.0, "0812345678");
        boolean ppSuccess = promptPay.processPayment();
        assertTrue("PromptPay payment processes successfully", ppSuccess);
        assertTrue("PromptPay transaction ref generated", promptPay.getTransactionRef().startsWith("PP-"));

        CashPayment cashValid = new CashPayment("PAY-2", "TKT-2", 80.0, 100.0);
        boolean cashSuccess = cashValid.processPayment();
        assertTrue("Cash payment with 100 THB for 80 THB fee succeeds", cashSuccess);
        assertEquals("Change is 20 THB", 20.0, cashValid.getChange(), 0.001);

        CashPayment cashInsufficient = new CashPayment("PAY-3", "TKT-3", 80.0, 50.0);
        boolean failSuccess = cashInsufficient.processPayment();
        assertTrue("Cash payment with insufficient amount fails", !failSuccess);
    }

    private static void testServerAuthoritativePaymentAmount() {
        System.out.println("\n5. Testing Server-Authoritative Payment Amount:");

        ParkingLot lot = new ParkingLot("Payment Test Lot", "Bangkok");
        ParkingFloor floor = new ParkingFloor(1, "Floor 1");
        floor.addSlot(SlotFactory.createSlot("PAY-01", 1, SlotType.STANDARD));
        lot.addFloor(floor);

        ParkingService service = new ParkingService(
                lot,
                new TicketRepository(),
                new PaymentRepository(),
                new DisplayBoard("PAY-BOARD")
        );
        Map<String, Object> checkIn = service.checkIn(VehicleType.CAR, "PAY-TEST", false);
        String ticketId = (String) checkIn.get("ticketId");
        service.fastForwardMinutes(60);

        double expectedFee = ((Number) service.calculateFee(ticketId).get("fee")).doubleValue();
        Map<String, Object> receipt = service.processPayment(
            ticketId, PaymentMethod.PROMPTPAY, null, null, null
        );

        assertTrue("Payment uses the server-calculated amount", ((Number) receipt.get("amount")).doubleValue() == expectedFee);
        assertTrue("Ticket stores the server-calculated amount", service.getTicketRepository()
                .findById(ticketId).get().getFee() == expectedFee);
    }

    private static void testReservationMembershipAndDashboard() {
        System.out.println("\n6. Testing Reservations, Memberships & Dashboard:");

        ParkingLot lot = new ParkingLot("Feature Test Lot", "Bangkok");
        ParkingFloor floor = new ParkingFloor(1, "Floor 1");
        floor.addSlot(SlotFactory.createSlot("FEATURE-01", 1, SlotType.STANDARD));
        lot.addFloor(floor);
        ParkingService service = new ParkingService(lot, new TicketRepository(), new PaymentRepository(), new DisplayBoard("FEATURE-BOARD"));

        LocalDateTime reservationStart = service.getCurrentTime().plusMinutes(30);
        Map<String, Object> reservation = service.createReservation("RSV-TEST", VehicleType.CAR, false,
                reservationStart, reservationStart.plusHours(2));
        service.fastForwardMinutes(30);
        Map<String, Object> checkIn = service.checkIn(VehicleType.CAR, "RSV-TEST", false);
        assertTrue("Reservation is consumed at check-in", reservation.get("reservationId").equals(checkIn.get("reservationId")));

        ParkingLot memberLot = new ParkingLot("Member Test Lot", "Bangkok");
        ParkingFloor memberFloor = new ParkingFloor(1, "Floor 1");
        memberFloor.addSlot(SlotFactory.createSlot("MEMBER-01", 1, SlotType.STANDARD));
        memberLot.addFloor(memberFloor);
        ParkingService memberService = new ParkingService(memberLot, new TicketRepository(), new PaymentRepository(), new DisplayBoard("MEMBER-BOARD"));
        LocalDate today = memberService.getCurrentTime().toLocalDate();
        memberService.createMembership("MEM-1", "Monthly Driver", "MEM-TEST", MembershipType.STANDARD_MEMBER, today, today.plusDays(30));
        Map<String, Object> memberCheckIn = memberService.checkIn(VehicleType.CAR, "MEM-TEST", false);
        String memberTicket = (String) memberCheckIn.get("ticketId");
        assertTrue("Check-in verifies active membership on the server", Boolean.TRUE.equals(memberCheckIn.get("memberVerified")));
        assertTrue("Monthly member is charged zero hourly fee", ((Number) memberService.calculateFee(memberTicket).get("fee")).doubleValue() == 0.0);

        Map<String, Object> dashboard = memberService.getDailyDashboard(today);
        assertTrue("Dashboard counts active membership", ((Number) dashboard.get("activeMembers")).longValue() == 1);
        assertTrue("Dashboard reports current occupancy", ((Number) dashboard.get("occupiedSlots")).longValue() == 1);

        ParkingLot vipLot = new ParkingLot("VIP Test Lot", "Bangkok");
        ParkingFloor vipFloor = new ParkingFloor(1, "Floor 1");
        vipFloor.addSlot(SlotFactory.createSlot("VIP-01", 1, SlotType.VIP));
        vipFloor.addSlot(SlotFactory.createSlot("VIP-02", 1, SlotType.STANDARD));
        vipLot.addFloor(vipFloor);
        ParkingService vipService = new ParkingService(vipLot, new TicketRepository(), new PaymentRepository(), new DisplayBoard("VIP-BOARD"));
        vipService.createMembership("VIP-1", "VIP Driver", "VIP-TEST", MembershipType.VIP_MEMBER, today, today.plusDays(30));
        Map<String, Object> vipCheckIn = vipService.checkIn(VehicleType.CAR, "VIP-TEST", false);
        assertTrue("VIP member receives VIP zone priority", "VIP-01".equals(vipCheckIn.get("slotNumber")));

        ParkingLot evLot = new ParkingLot("EV Member Test Lot", "Bangkok");
        ParkingFloor evFloor = new ParkingFloor(1, "Floor 1");
        evFloor.addSlot(SlotFactory.createSlot("EV-MEMBER-01", 1, SlotType.EV_CHARGING));
        evLot.addFloor(evFloor);
        ParkingService evService = new ParkingService(evLot, new TicketRepository(), new PaymentRepository(), new DisplayBoard("EV-MEMBER-BOARD"));
        evService.createMembership("EV-1", "EV Driver", "EV-MEMBER-TEST", MembershipType.EV_MEMBER, today, today.plusDays(30));
        Map<String, Object> evCheckIn = evService.checkIn(VehicleType.ELECTRIC_VEHICLE, "EV-MEMBER-TEST", false);
        assertTrue("EV member is assigned to charging slot", "EV-MEMBER-01".equals(evCheckIn.get("slotNumber")));
    }

    private static void testLostTicketIncludesParkingFee() {
        System.out.println("\n8. Testing Lost Ticket Parking Fee:");

        ParkingLot lot = new ParkingLot("Lost Ticket Test Lot", "Bangkok");
        ParkingFloor floor = new ParkingFloor(1, "Floor 1");
        floor.addSlot(SlotFactory.createSlot("LOST-01", 1, SlotType.STANDARD));
        lot.addFloor(floor);
        ParkingService service = new ParkingService(lot, new TicketRepository(), new PaymentRepository(), new DisplayBoard("LOST-BOARD"));

        String ticketId = (String) service.checkIn(VehicleType.CAR, "LOST-TEST", false).get("ticketId");
        service.fastForwardMinutes(180);
        Map<String, Object> lostFee = service.markTicketLost(ticketId);

        // 3 hours for a standard car is 80 THB; lost-ticket total is 80 + 300.
        assertTrue("Lost ticket includes the accumulated parking fee", ((Number) lostFee.get("fee")).doubleValue() == 380.0);
        assertTrue("Lost ticket strategy explains both charges", ((String) lostFee.get("rateDescription")).contains("ค่าจอดตามเวลาจริง"));
    }

    private static void testMembershipAndReservationPersistence() {
        System.out.println("\n7. Testing Membership & Reservation Persistence:");
        try {
            Path tempDirectory = Files.createTempDirectory("parking-persistence-test");
            Path membershipFile = tempDirectory.resolve("memberships.db");
            Path reservationFile = tempDirectory.resolve("reservations.db");
            LocalDate today = LocalDate.now();

            repository.MembershipRepository membershipRepository = new repository.MembershipRepository(membershipFile);
            membershipRepository.save(new domain.model.Membership(
                    "PERSIST-MEM", "Persistent Member", "PERSIST-001",
                    MembershipType.VIP_MEMBER, today, today.plusDays(30)));
            assertTrue("Membership survives repository reload", new repository.MembershipRepository(membershipFile)
                    .findValidByPlate("PERSIST-001", today).isPresent());

            repository.ReservationRepository reservationRepository = new repository.ReservationRepository(reservationFile);
            reservationRepository.save(new domain.model.Reservation(
                    "PERSIST-RSV", "PERSIST-002", VehicleType.CAR, false,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
            assertTrue("Reservation survives repository reload", new repository.ReservationRepository(reservationFile)
                    .findById("PERSIST-RSV").isPresent());
        } catch (java.io.IOException ex) {
            assertTrue("Persistence test can create temporary storage", false);
        }
    }

    private static void testAIServicesAndExplainability() {
        System.out.println("\n9. Testing AI Services & Explainable AI (XAI):");

        domain.ai.AIParkingService aiService = new domain.ai.AIParkingService();
        ParkingLot lot = new ParkingLot("AI Test Mall", "Bangkok");
        ParkingFloor f1 = new ParkingFloor(1, "ชั้น 1");
        f1.addSlot(SlotFactory.createSlot("F1-01", 1, SlotType.EV_CHARGING));
        f1.addSlot(SlotFactory.createSlot("F1-02", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("F1-03", 1, SlotType.COMPACT));
        lot.addFloor(f1);

        ParkingFloor f2 = new ParkingFloor(2, "ชั้น 2");
        f2.addSlot(SlotFactory.createSlot("F2-01", 2, SlotType.MOTORCYCLE));
        lot.addFloor(f2);

        // 1. Test EV with charging recommendation
        domain.ai.AIRecommendation evRec = aiService.recommendOptimalSlot(VehicleType.ELECTRIC_VEHICLE, true, lot);
        assertTrue("AI recommends EV charging slot F1-01 for EV", evRec.getSlotNumber().equals("F1-01"));
        assertTrue("AI provides transparent explanation", evRec.getPrimaryReason() != null && !evRec.getPrimaryReason().isEmpty());
        assertTrue("AI confidence is above 90%", evRec.getConfidence() >= 0.90);
        assertTrue("AI match score is high for optimal slot", evRec.getMatchScore() > 80.0);

        // 2. Test Motorcycle recommendation
        domain.ai.AIRecommendation motoRec = aiService.recommendOptimalSlot(VehicleType.MOTORCYCLE, false, lot);
        assertTrue("AI recommends motorcycle slot F2-01", motoRec.getSlotNumber().equals("F2-01"));
        assertTrue("Motorcycle explanation includes safety factors", motoRec.getPrimaryReason().contains("มอเตอร์ไซค์"));

        // 3. Test ANPR Simulator
        Map<String, Object> anprResult = aiService.simulateANPR("1กก-9999");
        assertTrue("ANPR correctly detects EV license plate", anprResult.get("detectedPlate").equals("1กก-9999"));
        assertTrue("ANPR classifies as ELECTRIC_VEHICLE", anprResult.get("detectedType").equals("ELECTRIC_VEHICLE"));
        assertTrue("ANPR confidence is > 95%", ((Number) anprResult.get("confidence")).doubleValue() > 95.0);

        // 4. Test Traffic Predictor & Copilot
        TicketRepository ticketRepo = new TicketRepository();
        PaymentRepository paymentRepo = new PaymentRepository();
        Map<String, Object> copilotResp = aiService.answerCopilotQuery("มีที่จอดว่างกี่ช่อง", lot, ticketRepo, paymentRepo, java.time.LocalDateTime.now());
        assertTrue("AI Copilot answers slot query with category", copilotResp.get("category").equals("SLOT_AVAILABILITY"));
        assertTrue("AI Copilot explanation contains available slots", ((String) copilotResp.get("answer")).contains("ว่าง"));
    }
}
