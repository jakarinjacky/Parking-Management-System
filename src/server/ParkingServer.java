package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import domain.ai.AIRecommendation;
import domain.enums.PaymentMethod;
import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.factory.SlotFactory;
import domain.model.ParkingFloor;
import domain.model.ParkingLot;
import domain.model.Slot;
import domain.model.Ticket;
import domain.model.Vehicle;
import domain.observer.DisplayBoard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import repository.PaymentRepository;
import repository.TicketRepository;
import service.ParkingService;
import util.SimpleJson;

/**
 * คลาส ParkingServer: ทำหน้าที่เป็น Web Server และ RESTful API Backend
 * ใช้ com.sun.net.httpserver.HttpServer ในการให้บริการ HTTP requests
 * สำหรับระบบบริหารจัดการที่จอดรถอัจฉริยะ (Smart Parking Management System)
 * และให้บริการไฟล์หน้าเว็บ Frontend (Static Files เช่น HTML, CSS, JS)
 */
public class ParkingServer {
    private static final int PORT = 8080; // พอร์ตสำหรับรันเซิร์ฟเวอร์
    private final ParkingService parkingService; // Business Logic Service หลักของระบบ
    private final HttpServer server; // อินสแตนซ์ของ HTTP Server
    private final Path webRoot; // โฟลเดอร์ต้นทางสำหรับเก็บไฟล์ Frontend

    /**
     * Constructor สำหรับเริ่มต้นสร้างเซิร์ฟเวอร์
     * @param parkingService เซอร์วิสหลักที่ดูแล Business Logic ของระบบที่จอดรถ
     * @param webRoot ที่อยู่โฟลเดอร์สำหรับเก็บไฟล์ Static Web (เช่น index.html, css, js)
     * @throws IOException หากเกิดข้อผิดพลาดในการเปิด Port หรือสร้าง Socket Server
     */
    public ParkingServer(ParkingService parkingService, Path webRoot) throws IOException {
        this.parkingService = parkingService;
        this.webRoot = webRoot;
        // สร้าง HttpServer ผูกเข้ากับ Port 8080
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ลงทะเบียนเส้นทาง (Routes) API และ Web Handler
        registerRoutes();
    }

    /**
     * กำหนดและลงทะเบียน Endpoints/URL Contexts ทั้งหมดที่เซิร์ฟเวอร์เปิดให้บริการ
     */
    private void registerRoutes() {
        // --- ส่วนของ API Endpoints ---
        server.createContext("/api/status", new ApiStatusHandler());            // ข้อมูลสถานะภาพรวมของลานจอดรถ
        server.createContext("/api/lot", new ApiLotHandler());                  // ข้อมูลผังลานจอดรถและสถานะแต่ละช่อง
        server.createContext("/api/park", new ApiParkHandler());                // การนำรถเข้าจอด (Check-in)
        server.createContext("/api/calculate-fee", new ApiCalculateFeeHandler()); // คำนวณค่าบริการที่จอดรถ
        server.createContext("/api/pay", new ApiPayHandler());                  // ชำระเงินค่าบริการ
        server.createContext("/api/exit", new ApiExitHandler());                // การนำรถออกจากลานจอด (Check-out)
        server.createContext("/api/lost-ticket", new ApiLostTicketHandler());   // จัดการกรณีตั๋วจอดรถสูญหาย
        server.createContext("/api/time-travel", new ApiTimeTravelHandler());   // จำลองเวลา (ข้ามเวลา/รีเซ็ตเวลา)
        server.createContext("/api/tickets", new ApiTicketsHandler());          // ดึงรายการตั๋วทั้งหมดในระบบ
        server.createContext("/api/payments", new ApiPaymentsHandler());        // ดึงรายการประวัติการชำระเงินทั้งหมด

        // --- ส่วนของ AI Services & Explainable AI (XAI) Endpoints ---
        server.createContext("/api/ai/recommend", new ApiAiRecommendHandler());  // จัดสรรและแนะนำช่องจอดด้วย AI พร้อมคำอธิบาย
        server.createContext("/api/ai/anpr", new ApiAiAnprHandler());            // สแกนป้ายทะเบียนและจำแนกประเภทรถ (AI Vision / ANPR)
        server.createContext("/api/ai/predict", new ApiAiPredictHandler());      // พยากรณ์ความหนาแน่นและราคา (Dynamic Pricing)
        server.createContext("/api/ai/insights", new ApiAiInsightsHandler());    // บทวิเคราะห์และข้อเสนอแนะเชิงบริหารสำหรับผู้บริหาร
        server.createContext("/api/ai/chat", new ApiAiChatHandler());            // AI Copilot Interactive Chatbot

        // --- ส่วนของ Static Web Files Handler (Frontend) ---
        server.createContext("/", new StaticFileHandler());
    }

    /**
     * เริ่มต้นการทำงานของเซิร์ฟเวอร์ (Start listening requests)
     */
    public void start() {
        server.setExecutor(null); // ใช้ default single/multi-thread executor
        server.start();
        System.out.println("=================================================");
        System.out.println("  Smart Parking Management System Server Started ");
        System.out.println("  URL: http://localhost:" + PORT);
        System.out.println("=================================================");
    }

    /**
     * หยุดการทำงานของเซิร์ฟเวอร์
     */
    public void stop() {
        server.stop(1);
    }

    // =========================================================================
    // --- API Handlers (คลาสจัดการคำขอ HTTP แต่ละ Endpoint) ---
    // =========================================================================

    /**
     * Handler: GET /api/status
     * ส่งคืนข้อมูลสถานะภาพรวม เช่น จำนวนช่องจอดว่าง, ความจุทั้งหมด,
     * อัตราการใช้งาน (Occupancy Rate), รายได้รวม และข้อความป้ายแจ้งสถานะ
     */
    private class ApiStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // รองรับ Preflight Request สำหรับ CORS
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            ParkingLot lot = parkingService.getParkingLot();
            DisplayBoard board = parkingService.getDisplayBoard();

            // รวบรวมข้อมูลสถิติภาพรวม
            Map<String, Object> data = new HashMap<>();
            data.put("lotName", lot.getName());
            data.put("address", lot.getAddress());
            data.put("simulatedTime", parkingService.getCurrentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            data.put("totalCapacity", lot.getTotalCapacity());
            data.put("totalAvailable", lot.getTotalAvailable());
            data.put("totalOccupied", lot.getTotalOccupied());
            data.put("occupancyRate", lot.getTotalCapacity() > 0 ?
                    Math.round(((double) lot.getTotalOccupied() / lot.getTotalCapacity()) * 100) : 0);
            data.put("totalRevenue", parkingService.getPaymentRepository().getTotalRevenue());
            data.put("displayBoardMessage", board.getLastUpdatedMessage());
            data.put("displayBoardTime", board.getLastUpdatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            // สรุปข้อมูลของแต่ละชั้น (จำนวนช่องทั้งหมด, ว่าง, ไม่ว่าง)
            List<Map<String, Object>> floorSummaries = new ArrayList<>();
            for (ParkingFloor f : lot.getFloors()) {
                Map<String, Object> fs = new HashMap<>();
                fs.put("floorNumber", f.getFloorNumber());
                fs.put("floorName", f.getFloorName());
                fs.put("totalSlots", f.getTotalCount());
                fs.put("availableSlots", f.getAvailableCount());
                fs.put("occupiedSlots", f.getOccupiedCount());
                floorSummaries.add(fs);
            }
            data.put("floors", floorSummaries);

            // ส่งข้อมูลกลับในรูปแบบ JSON พร้อม Status 200 OK
            sendJsonResponse(exchange, 200, data);
        }
    }

    /**
     * Handler: GET /api/lot
     * ส่งคืนข้อมูลผังลานจอดรถอย่างละเอียดทุกชั้น ทุกช่องจอด
     * พร้อมข้อมูลรถที่กำลังจอดอยู่ (ถ้ามี) เช่น ทะเบียน, เวลาที่เข้าจอด, ค่าบริการปัจจุบัน
     */
    private class ApiLotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            ParkingLot lot = parkingService.getParkingLot();
            List<Map<String, Object>> floorsList = new ArrayList<>();

            // วนลูปอ่านข้อมูลแต่ละชั้น
            for (ParkingFloor floor : lot.getFloors()) {
                Map<String, Object> floorMap = new HashMap<>();
                floorMap.put("floorNumber", floor.getFloorNumber());
                floorMap.put("floorName", floor.getFloorName());
                floorMap.put("totalSlots", floor.getTotalCount());
                floorMap.put("availableSlots", floor.getAvailableCount());

                // วนลูปอ่านรายละเอียดแต่ละช่องจอด
                List<Map<String, Object>> slotsList = new ArrayList<>();
                for (Slot slot : floor.getSlots()) {
                    Map<String, Object> sm = new HashMap<>();
                    sm.put("slotNumber", slot.getSlotNumber());
                    sm.put("floorNumber", slot.getFloorNumber());
                    sm.put("slotType", slot.getSlotType().name());
                    sm.put("slotTypeDisplay", slot.getSlotType().getDisplayName());
                    sm.put("status", slot.getStatus().name());
                    sm.put("statusLabel", slot.getStatus().getLabel());
                    sm.put("isAvailable", slot.isAvailable());

                    // ถ้ามีรถจอดอยู่ในช่องนี้ ให้ดึงข้อมูลรถและคำนวณค่าบริการปัจจุบัน
                    if (slot.getCurrentVehicle() != null) {
                        Vehicle v = slot.getCurrentVehicle();
                        Map<String, Object> vm = new HashMap<>();
                        vm.put("licensePlate", v.getLicensePlate());
                        vm.put("vehicleType", v.getType().name());
                        vm.put("vehicleTypeDisplay", v.getType().getDisplayName());

                        // จับคู่กับข้อมูลตั๋วจอดรถที่ใช้งานอยู่ (Active Ticket)
                        Optional<Ticket> ticketOpt = parkingService.getTicketRepository().findActiveByLicensePlate(v.getLicensePlate());
                        if (ticketOpt.isPresent()) {
                            Ticket t = ticketOpt.get();
                            vm.put("ticketId", t.getTicketId());
                            vm.put("entryTime", t.getEntryTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                            long minutes = t.calculateDuration(parkingService.getCurrentTime()).toMinutes();
                            vm.put("parkedDurationMinutes", minutes);
                            vm.put("rateStrategy", v.getPricingStrategy().getStrategyName());
                            vm.put("currentFee", v.getPricingStrategy().calculateFee(t.calculateDuration(parkingService.getCurrentTime()), v));
                        }
                        sm.put("vehicle", vm);
                    }
                    slotsList.add(sm);
                }
                floorMap.put("slots", slotsList);
                floorsList.add(floorMap);
            }

            sendJsonResponse(exchange, 200, floorsList);
        }
    }

    /**
     * Handler: POST /api/park
     * จัดการนำรถเข้าจอดในลานจอด (Check-in)
     * รับข้อมูล: { licensePlate: "...", vehicleType: "...", requiresCharging: "true/false" }
     * ผลลัพธ์: ออกตั๋วจอดรถ (Ticket) และระบุช่องจอดที่จัดสรรให้
     */
    private class ApiParkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            // อนุญาตเฉพาะเมธอด POST เท่านั้น
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                // อ่านข้อมูล JSON จาก Body ของ Request
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);

                String plate = req.get("licensePlate");
                String typeStr = req.get("vehicleType");
                boolean requiresCharging = "true".equalsIgnoreCase(req.get("requiresCharging"));

                // ตรวจสอบความถูกต้องของข้อมูล
                if (plate == null || typeStr == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "กรุณาระบุ licensePlate และ vehicleType"));
                    return;
                }

                VehicleType vehicleType = VehicleType.valueOf(typeStr.trim().toUpperCase());
                // ดำเนินการ Check-in ผ่าน ParkingService
                Map<String, Object> checkInResult = parkingService.checkIn(vehicleType, plate, requiresCharging);
                sendJsonResponse(exchange, 201, checkInResult);

            } catch (IllegalArgumentException | IllegalStateException ex) {
                // ข้อผิดพลาดจากเงื่อนไขทางธุรกิจ เช่น ช่องจอดเต็ม หรือรถทะเบียนนี้เข้าจอดอยู่แล้ว
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            } catch (Exception ex) {
                ex.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", "ข้อผิดพลาดภายในระบบ: " + ex.getMessage()));
            }
        }
    }

    /**
     * Handler: GET / POST /api/calculate-fee
     * คำนวณและพรีวิวค่าบริการที่จอดรถ ณ เวลาจำลองปัจจุบัน
     * สามารถระบุตั๋วผ่าน Query Parameter (?ticket=...) หรือ JSON Body ({ ticketIdOrPlate: "..." })
     */
    private class ApiCalculateFeeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String query = null;
                // ดึงรหัสตั๋วหรือทะเบียนรถจาก URL Query หรือ JSON Body
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String rawQuery = exchange.getRequestURI().getQuery();
                    if (rawQuery != null && rawQuery.contains("ticket=")) {
                        query = URLDecoder.decode(rawQuery.split("ticket=")[1].split("&")[0], StandardCharsets.UTF_8);
                    }
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = readRequestBody(exchange);
                    Map<String, String> req = SimpleJson.parseSimpleJson(body);
                    query = req.get("ticketIdOrPlate");
                }

                if (query == null || query.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, Map.of("error", "กรุณาระบุเลขที่ตั๋วหรือทะเบียนรถ"));
                    return;
                }

                // สั่งให้ Service คำนวณค่าบริการและรายละเอียดการจอด
                Map<String, Object> feePreview = parkingService.calculateFee(query);
                sendJsonResponse(exchange, 200, feePreview);

            } catch (IllegalArgumentException | IllegalStateException ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/pay
     * ดำเนินการชำระเงินค่าบริการที่จอดรถ
     * รองรับวิธีชำระเงิน: CASH (เงินสด), PROMPTPAY (สแกนคิวอาร์), CREDIT_CARD (บัตรเครดิต)
     */
    private class ApiPayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);

                String ticketId = req.get("ticketId");
                String methodStr = req.get("method");
                String amountStr = req.get("amount");

                if (ticketId == null || methodStr == null || amountStr == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "ข้อมูลไม่ครบถ้วน"));
                    return;
                }

                PaymentMethod method = PaymentMethod.valueOf(methodStr.trim().toUpperCase());
                double amount = Double.parseDouble(amountStr);

                // ดึงข้อมูลเพิ่มเติมตามวิธีชำระเงิน (เช่น เงินที่รับมา หรือข้อมูลบัตร)
                Double cashTendered = req.containsKey("cashTendered") ? Double.parseDouble(req.get("cashTendered")) : null;
                String cardNumber = req.get("cardNumber");
                String cardHolder = req.get("cardHolder");

                // บันทึกและประมวลผลการชำระเงินผ่าน Service
                Map<String, Object> receipt = parkingService.processPayment(
                        ticketId, method, amount, cashTendered, cardNumber, cardHolder
                );
                sendJsonResponse(exchange, 200, receipt);

            } catch (IllegalArgumentException | IllegalStateException ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            } catch (Exception ex) {
                ex.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/exit
     * ตรวจสอบการออกจากลานจอดรถที่ไม้กั้นทางออก (Exit Gate)
     * ตรวจสอบว่าตั๋วชำระเงินเรียบร้อยแล้วหรือไม่ คืนพื้นที่ช่องจอดรถ และอัปเดตสถานะตั๋วเป็น COMPLETED
     */
    private class ApiExitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String ticketId = req.get("ticketId");

                if (ticketId == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "กรุณาระบุ ticketId"));
                    return;
                }

                // เรียกฟังก์ชันเปิดประตูทางออก
                Map<String, Object> res = parkingService.exitGate(ticketId);
                sendJsonResponse(exchange, 200, res);

            } catch (IllegalArgumentException | IllegalStateException ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/lost-ticket
     * แจ้งตั๋วจอดรถสูญหาย โดยระบบจะคิดค่าปรับและค่าบริการตามกฎเกณฑ์ที่กำหนด
     */
    private class ApiLostTicketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String query = req.get("ticketIdOrPlate");

                Map<String, Object> res = parkingService.markTicketLost(query);
                sendJsonResponse(exchange, 200, res);
            } catch (Exception ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/time-travel
     * ระบบจำลองการเดินของเวลา (Simulation Clock)
     * รองรับคำสั่ง:
     * - ข้ามเวลาไปข้างหน้ากี่นาที (minutes)
     * - รีเซ็ตเวลากลับมาเป็นเวลาปัจจุบัน (action: "reset")
     */
    private class ApiTimeTravelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);

                if ("reset".equalsIgnoreCase(req.get("action"))) {
                    parkingService.resetTime(); // รีเซ็ตเวลาเป็นเวลาจริง
                } else if (req.containsKey("minutes")) {
                    long minutes = Long.parseLong(req.get("minutes"));
                    parkingService.fastForwardMinutes(minutes); // ข้ามเวลาไปข้างหน้า
                }

                Map<String, Object> resp = new HashMap<>();
                resp.put("simulatedTime", parkingService.getCurrentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                resp.put("message", "ปรับเปลี่ยนเวลาจำลองสำเร็จ");
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: GET /api/tickets
     * ส่งคืนรายการประวัติตั๋วจอดรถทั้งหมดในระบบ (ทั้งที่กำลังจอด ชำระแล้ว หรือออกจากลานแล้ว)
     */
    private class ApiTicketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            List<Ticket> tickets = parkingService.getTicketRepository().findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Ticket t : tickets) {
                Map<String, Object> m = new HashMap<>();
                m.put("ticketId", t.getTicketId());
                m.put("licensePlate", t.getLicensePlate());
                m.put("vehicleType", t.getVehicleType().name());
                m.put("vehicleTypeDisplay", t.getVehicleType().getDisplayName());
                m.put("floorNumber", t.getFloorNumber());
                m.put("slotNumber", t.getSlotNumber());
                m.put("entryTime", t.getEntryTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                m.put("exitTime", t.getExitTime() != null ? t.getExitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-");
                m.put("status", t.getStatus().name());
                m.put("statusDescription", t.getStatus().getDescription());
                m.put("fee", t.getFee());
                list.add(m);
            }
            sendJsonResponse(exchange, 200, list);
        }
    }

    /**
     * Handler: GET /api/payments
     * ส่งคืนรายการประวัติการชำระเงินทั้งหมดและยอดรวมรายได้สะสม
     */
    private class ApiPaymentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            PaymentRepository repo = parkingService.getPaymentRepository();
            List<Map<String, Object>> list = new ArrayList<>();
            for (var p : repo.findAll()) {
                Map<String, Object> m = new HashMap<>();
                m.put("paymentId", p.getPaymentId());
                m.put("ticketId", p.getTicketId());
                m.put("amount", p.getAmount());
                m.put("method", p.getMethod().name());
                m.put("methodLabel", p.getMethod().getLabel());
                m.put("status", p.getStatus().name());
                m.put("ref", p.getTransactionRef());
                m.put("paymentTime", p.getPaymentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                list.add(m);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("totalRevenue", repo.getTotalRevenue());
            resp.put("payments", list);
            sendJsonResponse(exchange, 200, resp);
        }
    }

    // =========================================================================
    // --- AI Services & Explainable AI (XAI) Handlers ---
    // =========================================================================

    /**
     * Handler: POST /api/ai/recommend
     * แนะนำช่องจอดที่เหมาะสมที่สุดด้วย AI พร้อมคำอธิบายเหตุผลอย่างโปร่งใส (XAI)
     */
    private class ApiAiRecommendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String typeStr = req.get("vehicleType");
                boolean reqCharging = "true".equalsIgnoreCase(req.get("requiresCharging"));

                VehicleType vType = VehicleType.CAR;
                if (typeStr != null && !typeStr.isEmpty()) {
                    try {
                        vType = VehicleType.valueOf(typeStr.trim().toUpperCase());
                    } catch (Exception ignored) {}
                }

                AIRecommendation rec = parkingService.getAIParkingService()
                        .recommendOptimalSlot(vType, reqCharging, parkingService.getParkingLot());

                Map<String, Object> res = new HashMap<>();
                res.put("slotNumber", rec.getSlotNumber());
                res.put("floorNumber", rec.getFloorNumber());
                res.put("slotType", rec.getSlotType().name());
                res.put("matchScore", rec.getMatchScore());
                res.put("confidence", rec.getConfidence());
                res.put("primaryReason", rec.getPrimaryReason());
                res.put("factors", rec.getFactors());
                res.put("energyEfficiency", rec.getEnergyEfficiency());
                res.put("congestionImpact", rec.getCongestionImpact());

                sendJsonResponse(exchange, 200, res);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/ai/anpr
     * จำลองระบบกล้อง AI ตรวจจับป้ายทะเบียน (ANPR) และจำแนกประเภทรถยนต์
     */
    private class ApiAiAnprHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String query = req.get("licensePlate");

                Map<String, Object> result = parkingService.getAIParkingService().simulateANPR(query);
                sendJsonResponse(exchange, 200, result);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * Handler: GET /api/ai/predict
     * พยากรณ์อัตราความหนาแน่นและการปรับราคาแบบ Dynamic Pricing
     */
    private class ApiAiPredictHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                Map<String, Object> forecast = parkingService.getAIParkingService()
                        .predictTrafficAndPricing(parkingService.getCurrentTime(), parkingService.getParkingLot());
                sendJsonResponse(exchange, 200, forecast);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * Handler: GET /api/ai/insights
     * บทวิเคราะห์เชิงบริหารและข้อเสนอแนะของ AI สำหรับผู้จัดการลานจอดรถ
     */
    private class ApiAiInsightsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                Map<String, Object> insights = parkingService.getAIParkingService()
                        .generateExecutiveInsights(parkingService.getParkingLot(),
                                parkingService.getTicketRepository(),
                                parkingService.getPaymentRepository(),
                                parkingService.getCurrentTime());
                sendJsonResponse(exchange, 200, insights);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/ai/chat
     * AI Copilot สนทนาและตอบคำถามเกี่ยวกับสถานะระบบ กฎ OOP และคำแนะนำ
     */
    private class ApiAiChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String msg = req.get("message");

                Map<String, Object> resp = parkingService.getAIParkingService()
                        .answerCopilotQuery(msg,
                                parkingService.getParkingLot(),
                                parkingService.getTicketRepository(),
                                parkingService.getPaymentRepository(),
                                parkingService.getCurrentTime());
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // =========================================================================
    // --- Static File Serving (จัดการส่งไฟล์หน้าบ้าน HTML, CSS, JS) ---
    // =========================================================================

    /**
     * Handler สำหรับการส่งไฟล์หน้าเว็บ Frontend (Static Files)
     * หากเรียก Path ว่าง ("/") จะส่งไฟล์ index.html ให้โดยอัตโนมัติ
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // ตรวจสอบความปลอดภัย ป้องกัน Path Traversal Attack
            Path file = webRoot.resolve(path.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.exists(file) || Files.isDirectory(file)) {
                file = webRoot.resolve("index.html");
            }

            // หากไม่พบไฟล์ ส่งรหัส 404 Not Found
            if (!Files.exists(file)) {
                String notFound = "<h1>404 Not Found</h1>";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            // ตรวจหา Content-Type ตามนามสกุลไฟล์ และส่งข้อมูลไฟล์กลับไป
            String contentType = determineContentType(file.getFileName().toString());
            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * วิเคราะห์และกำหนด MIME Content-Type ตามนามสกุลไฟล์
     * @param fileName ชื่อไฟล์ที่ต้องการตรวจสอบ
     * @return ค่า Content-Type เช่น text/html, text/css, application/javascript
     */
    private static String determineContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html; charset=UTF-8";
        if (fileName.endsWith(".css")) return "text/css; charset=UTF-8";
        if (fileName.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (fileName.endsWith(".json")) return "application/json; charset=UTF-8";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=UTF-8";
    }

    /**
     * ส่ง HTTP Header สำหรับเปิดใช้งาน CORS (Cross-Origin Resource Sharing)
     * เพื่อให้เบราว์เซอร์อนุญาตให้เรียกใช้งาน API ข้ามพอร์ตหรือโดเมนได้
     * @param exchange อินสแตนซ์ HttpExchange ของคำขอปัจจุบัน
     * @throws IOException หากเกิดข้อผิดพลาดในการส่ง Response Headers
     */
    private static void sendCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    /**
     * แปลง Object เป็น JSON และส่ง HTTP Response กลับไปหา Client
     * @param exchange อินสแตนซ์ HttpExchange
     * @param statusCode รหัสสถานะ HTTP (เช่น 200, 201, 400, 404, 500)
     * @param data ข้อมูล Object ที่จะถูก Serialize เป็น JSON String
     * @throws IOException หากเกิดข้อผิดพลาดในการเขียน Response Body
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = SimpleJson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * อ่านข้อมูล Text Body ที่ส่งมาจาก HTTP Request (Payload) และแปลงเป็น String UTF-8
     * @param exchange อินสแตนซ์ HttpExchange
     * @return ข้อความสตริงทั้งหมดที่อยู่ใน Request Body
     * @throws IOException หากเกิดข้อผิดพลาดในการอ่าน InputStream
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    // =========================================================================
    // --- Main Entry Point & Demo Seeding ---
    // =========================================================================

    /**
     * จุดเริ่มต้นการทำงานของโปรแกรม (Main Entry Point)
     * 1. สร้างโครงสร้างลานจอดรถ (ParkingLot) พร้อมชั้น (Floors) และช่องจอด (Slots)
     * 2. ติดตั้ง Repository และ Service
     * 3. ใส่ข้อมูลจำลองรถที่เข้าจอดล่วงหน้า (Demo Data)
     * 4. สั่งเริ่มต้น HTTP Web Server
     * @param args อาร์กิวเมนต์จาก Command Line
     * @throws Exception หากเกิดข้อผิดพลาดขณะเริ่มระบบ
     */
    public static void main(String[] args) throws Exception {
        // 1. สร้างอินสแตนซ์ลานจอดรถ (ParkingLot)
        ParkingLot lot = new ParkingLot("Grand Smart Parking Plaza", "88 Sukhumvit Rd, Bangkok");

        // ชั้นที่ 1: สำหรับรถ EV Charging, VIP และรถยนต์ทั่วไป
        ParkingFloor f1 = new ParkingFloor(1, "ชั้น 1: VIP, EV Charging & รถเก๋ง");
        f1.addSlot(SlotFactory.createSlot("F1-01", 1, SlotType.EV_CHARGING));
        f1.addSlot(SlotFactory.createSlot("F1-02", 1, SlotType.EV_CHARGING));
        f1.addSlot(SlotFactory.createSlot("F1-03", 1, SlotType.EV_CHARGING));
        f1.addSlot(SlotFactory.createSlot("F1-04", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("F1-05", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("F1-06", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("F1-07", 1, SlotType.STANDARD));
        f1.addSlot(SlotFactory.createSlot("F1-08", 1, SlotType.COMPACT));
        lot.addFloor(f1);

        // ชั้นที่ 2: สำหรับรถเก๋งทั่วไปและรถขนาดกะทัดรัด (Compact Cars)
        ParkingFloor f2 = new ParkingFloor(2, "ชั้น 2: รถเก๋งทั่วไป & รถขนาดกะทัดรัด");
        f2.addSlot(SlotFactory.createSlot("F2-01", 2, SlotType.STANDARD));
        f2.addSlot(SlotFactory.createSlot("F2-02", 2, SlotType.STANDARD));
        f2.addSlot(SlotFactory.createSlot("F2-03", 2, SlotType.STANDARD));
        f2.addSlot(SlotFactory.createSlot("F2-04", 2, SlotType.STANDARD));
        f2.addSlot(SlotFactory.createSlot("F2-05", 2, SlotType.COMPACT));
        f2.addSlot(SlotFactory.createSlot("F2-06", 2, SlotType.COMPACT));
        f2.addSlot(SlotFactory.createSlot("F2-07", 2, SlotType.COMPACT));
        f2.addSlot(SlotFactory.createSlot("F2-08", 2, SlotType.COMPACT));
        lot.addFloor(f2);

        // ชั้นที่ 3: สำหรับรถจักรยานยนต์และรถขนาดใหญ่ (Trucks / Vans)
        ParkingFloor f3 = new ParkingFloor(3, "ชั้น 3: รถจักรยานยนต์ & รถขนาดใหญ่");
        f3.addSlot(SlotFactory.createSlot("F3-01", 3, SlotType.MOTORCYCLE));
        f3.addSlot(SlotFactory.createSlot("F3-02", 3, SlotType.MOTORCYCLE));
        f3.addSlot(SlotFactory.createSlot("F3-03", 3, SlotType.MOTORCYCLE));
        f3.addSlot(SlotFactory.createSlot("F3-04", 3, SlotType.MOTORCYCLE));
        f3.addSlot(SlotFactory.createSlot("F3-05", 3, SlotType.LARGE));
        f3.addSlot(SlotFactory.createSlot("F3-06", 3, SlotType.LARGE));
        lot.addFloor(f3);

        // 2. สร้างที่จัดเก็บข้อมูลจำลอง (Repositories) และ Service
        TicketRepository ticketRepo = new TicketRepository();
        PaymentRepository paymentRepo = new PaymentRepository();
        DisplayBoard displayBoard = new DisplayBoard("BOARD-MAIN-GATE");

        ParkingService service = new ParkingService(lot, ticketRepo, paymentRepo, displayBoard);

        // 3. จำลองการจอดรถล่วงหน้าเพื่อให้มีข้อมูลในระบบพร้อมทดสอบ
        seedDemoData(service, lot, ticketRepo);

        // 4. สตาร์ตเซิร์ฟเวอร์
        Path webDir = Paths.get("web").toAbsolutePath();
        ParkingServer server = new ParkingServer(service, webDir);
        server.start();
    }

    /**
     * ฟังก์ชันสำหรับใส่ข้อมูลจำลอง (Seed Demo Data)
     * จำลองเหตุการณ์รถเข้าจอดในช่วงเวลาต่าง ๆ เพื่อให้ระบบมีข้อมูลทดสอบที่สมจริง
     * @param service เซอร์วิสระบบที่จอดรถ
     * @param lot อ็อบเจกต์ลานจอดรถ
     * @param ticketRepo ตัวจัดการข้อมูลตั๋ว
     */
    private static void seedDemoData(ParkingService service, ParkingLot lot, TicketRepository ticketRepo) {
        try {
            // รถคันที่ 1: Tesla Model Y (EV) เข้าจอด 2 ชั่วโมงที่แล้ว
            service.fastForwardMinutes(-120);
            service.checkIn(VehicleType.ELECTRIC_VEHICLE, "1กก-9999", true);

            // รถคันที่ 2: Honda Civic (Car) เข้าจอด 75 นาทีที่แล้ว
            service.fastForwardMinutes(45);
            service.checkIn(VehicleType.CAR, "4ขข-1234", false);

            // รถคันที่ 3: Yamaha XMAX (Motorcycle) เข้าจอด 30 นาทีที่แล้ว
            service.fastForwardMinutes(45);
            service.checkIn(VehicleType.MOTORCYCLE, "9กข-777", false);

            // ปรับเวลาจำลองกลับมาที่เวลาปัจจุบัน
            service.fastForwardMinutes(30);

        } catch (Exception e) {
            System.err.println("Seed error: " + e.getMessage());
        }
    }
}

