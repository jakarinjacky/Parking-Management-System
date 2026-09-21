package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import domain.ai.AIRecommendation;
import domain.enums.MembershipType;
import domain.enums.PaymentMethod;
import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.enums.UserRole;
import domain.factory.SlotFactory;
import domain.hardware.GateLane;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import repository.MembershipRepository;
import repository.ParkingHistoryRepository;
import repository.PaymentRepository;
import repository.ReservationRepository;
import repository.TicketRepository;
import service.ParkingService;
import util.SimpleJson;

/**
 * คลาส ParkingServer: ทำหน้าที่เป็น Web Server และ RESTful API Backend
 * ใช้ com.sun.net.httpserver.HttpServer ในการให้บริการ HTTP requests
 * สำหรับระบบบริหารจัดการที่จอดรถอัจฉริยะ (Smart Parking Management System)
 * และให้บริการไฟล์หน้าเว็บ Frontend (Static Files เช่น HTML, CSS, JS)
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class ParkingServer {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")); // พอร์ตสำหรับรันเซิร์ฟเวอร์
    private final ParkingService parkingService; // Business Logic Service หลักของระบบ
    private final HttpServer server; // อินสแตนซ์ของ HTTP Server
    private final Path webRoot; // โฟลเดอร์ต้นทางสำหรับเก็บไฟล์ Frontend
    private final boolean platformOnly = Boolean.parseBoolean(System.getenv().getOrDefault("PLATFORM_ONLY", "false"));
    private final Map<String, EmployeeSession> activeSessions = new ConcurrentHashMap<>();

    private static final Map<String, EmployeeAccount> SYSTEM_USERS = new HashMap<>();
    static {
        SYSTEM_USERS.put("owner", new EmployeeAccount("owner", "owner123", "owner", "เจ้าของลานจอดรถ"));
        SYSTEM_USERS.put("admin", new EmployeeAccount("admin", "admin123", "admin", "ผู้ดูแลระบบ"));
        SYSTEM_USERS.put("staff01", new EmployeeAccount("staff01", "staff123", "staff", "พนักงานจุดเข้า-ออกรถ 1"));
        SYSTEM_USERS.put("staff02", new EmployeeAccount("staff02", "staff123", "staff", "พนักงานจุดเข้า-ออกรถ 2"));
        SYSTEM_USERS.put("staff03", new EmployeeAccount("staff03", "staff123", "staff", "พนักงานชำระเงิน / ทางออก"));
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private static class EmployeeAccount {
        private final String username;
        private final String password;
        private final String role;
        private final String displayName;

        private EmployeeAccount(String username, String password, String role, String displayName) {
            this.username = username;
            this.password = password;
            this.role = role;
            this.displayName = displayName;
        }
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private static class EmployeeSession {
        private final EmployeeAccount account;
        private final LocalDateTime loginTime;

        private EmployeeSession(EmployeeAccount account) {
            this.account = account;
            this.loginTime = LocalDateTime.now();
        }
    }

    /**
     * Constructor สำหรับเริ่มต้นสร้างเซิร์ฟเวอร์
     * @param parkingService เซอร์วิสหลักที่ดูแล Business Logic ของระบบที่จอดรถ
     * @param webRoot ที่อยู่โฟลเดอร์สำหรับเก็บไฟล์ Static Web (เช่น index.html, css, js)
     * @throws IOException หากเกิดข้อผิดพลาดในการเปิด Port หรือสร้าง Socket Server
     */
    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object ParkingServer
    public ParkingServer(ParkingService parkingService, Path webRoot) throws IOException {
        this.parkingService = parkingService;
        this.webRoot = webRoot;
        // สร้าง HttpServer ผูกเข้ากับ Port 8080
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ลงทะเบียนเส้นทาง (Routes) API และ Web Handler
        if (platformOnly) server.createContext("/", new StaticFileHandler());
        else registerRoutes();
        // Platform keeps its own tenant-scoped store/session; legacy data is not migrated implicitly.
        server.createContext("/api/platform", new platform.PlatformHandler());
    }

    /**
     * กำหนดและลงทะเบียน Endpoints/URL Contexts ทั้งหมดที่เซิร์ฟเวอร์เปิดให้บริการ
     */
    // [OOP: METHOD] Method registerRoutes() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private void registerRoutes() {
        server.createContext("/api/login", new ApiLoginHandler());
        server.createContext("/api/logout", new ApiLogoutHandler());
        server.createContext("/api/session", new ApiSessionHandler());

        // --- ส่วนของ API Endpoints ---
        registerProtectedRoute("/api/status", new ApiStatusHandler());
        registerProtectedRoute("/api/lot", new ApiLotHandler());
        registerProtectedRoute("/api/park", new ApiParkHandler());
        registerProtectedRoute("/api/calculate-fee", new ApiCalculateFeeHandler());
        registerProtectedRoute("/api/pay", new ApiPayHandler());
        registerProtectedRoute("/api/exit", new ApiExitHandler());
        registerProtectedRoute("/api/lost-ticket", new ApiLostTicketHandler());
        registerProtectedRoute("/api/time-travel", new ApiTimeTravelHandler());
        registerProtectedRoute("/api/tickets", new ApiTicketsHandler());
        registerProtectedRoute("/api/payments", new ApiPaymentsHandler());
        registerProtectedRoute("/api/reservations", new ApiReservationHandler());
        registerProtectedRoute("/api/memberships", new ApiMembershipHandler());
        registerProtectedRoute("/api/dashboard/daily", new ApiDailyDashboardHandler());
        registerProtectedRoute("/api/history", new ApiParkingHistoryHandler());

        // --- ส่วนของ AI Services & Explainable AI (XAI) Endpoints ---
        registerProtectedRoute("/api/ai/recommend", new ApiAiRecommendHandler());
        registerProtectedRoute("/api/ai/anpr", new ApiAiAnprHandler());
        registerProtectedRoute("/api/ai/anpr-entry", new ApiAiAnprEntryHandler());
        registerProtectedRoute("/api/ai/predict", new ApiAiPredictHandler());
        registerProtectedRoute("/api/ai/insights", new ApiAiInsightsHandler());
        registerProtectedRoute("/api/ai/chat", new ApiAiChatHandler());
        registerProtectedRoute("/api/hardware/status", new ApiHardwareStatusHandler());
        registerProtectedRoute("/api/hardware/gate", new ApiHardwareGateHandler());

        // --- ส่วนของ Static Web Files Handler (Frontend) ---
        server.createContext("/", new StaticFileHandler());
    }

    // [OOP: METHOD] Method registerProtectedRoute() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private void registerProtectedRoute(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            EmployeeSession session = getSession(exchange);
            if (session == null) {
                sendUnauthorized(exchange);
                return;
            }

            UserRole role = UserRole.fromCode(session.account.role);
            if (!exchange.getRequestURI().getPath().equals(path)) {
                sendJsonResponse(exchange, 404, Map.of("error", "ไม่พบ API"));
                return;
            }
            if (role == null || !role.allows(path)) {
                sendJsonResponse(exchange, 403, Map.of("error", "บัญชีนี้ไม่มีสิทธิ์ใช้งานส่วนนี้"));
                return;
            }
            handler.handle(exchange);
        });
    }

    // [OOP: METHOD] Method isAuthenticated() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private boolean isAuthenticated(HttpExchange exchange) {
        String token = getCookieValue(exchange, "parking_session_token");
        if (token == null || token.isBlank()) {
            return false;
        }
        EmployeeSession session = activeSessions.get(token);
        return session != null;
    }

    // [OOP: METHOD] Method getSession() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private EmployeeSession getSession(HttpExchange exchange) {
        String token = getCookieValue(exchange, "parking_session_token");
        if (token == null || token.isBlank()) {
            return null;
        }
        return activeSessions.get(token);
    }

    // [OOP: METHOD] Method getCookieValue() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static String getCookieValue(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }

        for (String cookieHeader : cookies) {
            String[] parts = cookieHeader.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
        }
        return null;
    }

    // [OOP: METHOD] Method setCookie() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void setCookie(HttpExchange exchange, String name, String value, int maxAgeSeconds) {
        String cookie = name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax";
        if (maxAgeSeconds >= 0) {
            cookie += "; Max-Age=" + maxAgeSeconds;
        }
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    // [OOP: METHOD] Method sendUnauthorized() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 401, Map.of(
                "error", "กรุณาเข้าสู่ระบบก่อนใช้งาน",
                "requiresLogin", true
        ));
    }

    /**
     * เริ่มต้นการทำงานของเซิร์ฟเวอร์ (Start listening requests)
     */
    // [OOP: METHOD] Method start() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void start() {
        server.setExecutor(null); // ใช้ default single/multi-thread executor
        server.start();
        System.out.println("=================================================");
        System.out.println("  Smart Parking Management System Server Started ");
        System.out.println("  URL: http://localhost:" + PORT);
        System.out.println("  Login accounts:");
        for (Map.Entry<String, EmployeeAccount> entry : SYSTEM_USERS.entrySet()) {
            System.out.println("    - " + entry.getKey() + " / " + entry.getValue().password + " (" + entry.getValue().displayName + ")");
        }
        System.out.println("=================================================");
    }

    /**
     * หยุดการทำงานของเซิร์ฟเวอร์
     */
    // [OOP: METHOD] Method stop() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void stop() {
        server.stop(1);
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiLoginHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
                String username = req.get("username");
                String password = req.get("password");

                if (username == null || password == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "กรุณาระบุชื่อผู้ใช้และรหัสผ่าน"));
                    return;
                }

                EmployeeAccount account = SYSTEM_USERS.get(username.trim().toLowerCase());
                if (account == null || !account.password.equals(password.trim())) {
                    sendJsonResponse(exchange, 401, Map.of("error", "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง"));
                    return;
                }

                String token = UUID.randomUUID().toString();
                EmployeeSession session = new EmployeeSession(account);
                activeSessions.put(token, session);
                setCookie(exchange, "parking_session_token", token, 60 * 60 * 8);
                System.out.println("[LOGIN] " + account.displayName + " (" + account.role + ") logged in from " + exchange.getRemoteAddress());

                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "เข้าสู่ระบบสำเร็จ");
                resp.put("username", account.username);
                resp.put("displayName", account.displayName);
                resp.put("role", account.role);
                resp.put("roleLabel", UserRole.fromCode(account.role).getLabel());
                resp.put("loginTime", session.loginTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                resp.put("token", token);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, Map.of("error", ex.getMessage()));
            }
        }
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiSessionHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            EmployeeSession session = getSession(exchange);
            if (session == null) {
                sendJsonResponse(exchange, 401, Map.of("error", "Session expired", "requiresLogin", true));
                return;
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("authenticated", true);
            resp.put("username", session.account.username);
            resp.put("displayName", session.account.displayName);
            resp.put("role", session.account.role);
            resp.put("roleLabel", UserRole.fromCode(session.account.role).getLabel());
            resp.put("loginTime", session.loginTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            sendJsonResponse(exchange, 200, resp);
        }
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiLogoutHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            String token = getCookieValue(exchange, "parking_session_token");
            if (token != null) {
                activeSessions.remove(token);
            }
            setCookie(exchange, "parking_session_token", "", 0);
            sendJsonResponse(exchange, 200, Map.of("success", true, "message", "ออกจากระบบสำเร็จ"));
        }
    }

    // =========================================================================
    // --- API Handlers (คลาสจัดการคำขอ HTTP แต่ละ Endpoint) ---
    // =========================================================================

    /**
     * Handler: GET /api/status
     * ส่งคืนข้อมูลสถานะภาพรวม เช่น จำนวนช่องจอดว่าง, ความจุทั้งหมด,
     * อัตราการใช้งาน (Occupancy Rate), รายได้รวม และข้อความป้ายแจ้งสถานะ
     */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiStatusHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
            if ("owner".equals(getSession(exchange).account.role)) {
                data.put("totalRevenue", parkingService.getPaymentRepository().getTotalRevenue());
            }
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

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiReservationHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendCors(exchange); return; }
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<Map<String, Object>> reservations = new ArrayList<>();
                    for (var reservation : parkingService.getReservationRepository().findAll()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("reservationId", reservation.getReservationId());
                        item.put("licensePlate", reservation.getLicensePlate());
                        item.put("vehicleType", reservation.getVehicleType().name());
                        item.put("startTime", reservation.getStartTime().toString());
                        item.put("endTime", reservation.getEndTime().toString());
                        item.put("cancelled", reservation.isCancelled());
                        item.put("checkedIn", reservation.isCheckedIn());
                        reservations.add(item);
                    }
                    sendJsonResponse(exchange, 200, reservations);
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed")); return; }
                Map<String, String> req = SimpleJson.parseSimpleJson(readRequestBody(exchange));
                Map<String, Object> result = parkingService.createReservation(
                        req.get("licensePlate"), VehicleType.valueOf(req.get("vehicleType").toUpperCase()),
                        "true".equalsIgnoreCase(req.get("requiresCharging")),
                        LocalDateTime.parse(req.get("startTime")), LocalDateTime.parse(req.get("endTime")));
                sendJsonResponse(exchange, 201, result);
            } catch (IllegalArgumentException | IllegalStateException ex) { sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage())); }
        }
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiMembershipHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendCors(exchange); return; }
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<Map<String, Object>> members = new ArrayList<>();
                    for (var member : parkingService.getMembershipRepository().findAll()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("memberId", member.getMemberId()); item.put("memberName", member.getMemberName());
                        item.put("licensePlate", member.getLicensePlate()); item.put("validFrom", member.getValidFrom().toString());
                        item.put("validUntil", member.getValidUntil().toString());
                        item.put("membershipType", member.getMembershipType().name());
                        item.put("membershipTypeDisplay", member.getMembershipType().getDisplayName()); members.add(item);
                    }
                    sendJsonResponse(exchange, 200, members); return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed")); return; }
                Map<String, String> req = SimpleJson.parseSimpleJson(readRequestBody(exchange));
                String membershipTypeValue = req.get("membershipType");
                MembershipType membershipType = membershipTypeValue == null || membershipTypeValue.isBlank()
                    ? MembershipType.STANDARD_MEMBER
                    : MembershipType.valueOf(membershipTypeValue.trim().toUpperCase());
                sendJsonResponse(exchange, 201, parkingService.createMembership(req.get("memberId"), req.get("memberName"),
                    req.get("licensePlate"), membershipType, LocalDate.parse(req.get("validFrom")), LocalDate.parse(req.get("validUntil"))));
            } catch (IllegalArgumentException ex) { sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage())); }
        }
    }

    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiDailyDashboardHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendCors(exchange); return; }
            try {
                String query = exchange.getRequestURI().getQuery();
                LocalDate date = LocalDate.now();
                if (query != null && query.startsWith("date=")) date = LocalDate.parse(query.substring(5));
                sendJsonResponse(exchange, 200, parkingService.getDailyDashboard(date));
            } catch (IllegalArgumentException ex) { sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage())); }
        }
    }

    /** GET /api/history?from=YYYY-MM-DD&to=YYYY-MM-DD&plate=ทะเบียน */
    // [OOP: CLASS] Handler สำหรับค้นและสรุปประวัติรถเข้าออกย้อนหลังไม่เกิน 3 เดือน
    private class ApiParkingHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendCors(exchange); return; }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                Map<String, String> params = parseQueryParameters(exchange.getRequestURI().getRawQuery());
                LocalDate from = params.get("from") == null || params.get("from").isBlank()
                        ? null : LocalDate.parse(params.get("from"));
                LocalDate to = params.get("to") == null || params.get("to").isBlank()
                        ? null : LocalDate.parse(params.get("to"));
                sendJsonResponse(exchange, 200,
                        parkingService.getParkingHistory(from, to, params.get("plate")));
            } catch (IllegalArgumentException ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            }
        }
    }

    private static Map<String, String> parseQueryParameters(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return params;
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    /**
     * Handler: GET /api/lot
     * ส่งคืนข้อมูลผังลานจอดรถอย่างละเอียดทุกชั้น ทุกช่องจอด
     * พร้อมข้อมูลรถที่กำลังจอดอยู่ (ถ้ามี) เช่น ทะเบียน, เวลาที่เข้าจอด, ค่าบริการปัจจุบัน
     */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiLotHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiParkHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiCalculateFeeHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiPayHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

                if (ticketId == null || methodStr == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "ข้อมูลไม่ครบถ้วน"));
                    return;
                }

                PaymentMethod method = PaymentMethod.valueOf(methodStr.trim().toUpperCase());

                // ดึงข้อมูลเพิ่มเติมตามวิธีชำระเงิน (เช่น เงินที่รับมา หรือข้อมูลบัตร)
                Double cashTendered = req.containsKey("cashTendered") ? Double.parseDouble(req.get("cashTendered")) : null;
                String cardNumber = req.get("cardNumber");
                String cardHolder = req.get("cardHolder");

                // บันทึกและประมวลผลการชำระเงินผ่าน Service
                Map<String, Object> receipt = parkingService.processPayment(
                    ticketId, method, cashTendered, cardNumber, cardHolder
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiExitHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiLostTicketHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiTimeTravelHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiTicketsHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            List<Ticket> tickets = parkingService.getTicketRepository().findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Ticket t : tickets) {
                if ("staff".equals(getSession(exchange).account.role) && t.getExitTime() != null) continue;
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiPaymentsHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiRecommendHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiAnprHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                String query = req.get("licensePlate");

                Map<String, Object> result = parkingService.scanEntryCamera(query);
                sendJsonResponse(exchange, 200, result);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * Handler: POST /api/ai/anpr-entry
     * ตรวจทะเบียนจากกล้องและเปิดไม้กั้นอัตโนมัติเฉพาะสมาชิกที่ยังมีสิทธิ์ใช้งาน
     */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiAnprEntryHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> req = SimpleJson.parseSimpleJson(body);
                Map<String, Object> scan = parkingService.scanEntryCamera(req.get("licensePlate"));
                String plate = (String) scan.get("detectedPlate");
                Optional<domain.model.Membership> member = parkingService.getMembershipRepository()
                        .findValidByPlate(plate, parkingService.getCurrentTime().toLocalDate());

                Map<String, Object> response = new HashMap<>(scan);
                response.put("membershipMatched", member.isPresent());
                response.put("autoEntry", false);

                if (member.isPresent()) {
                    VehicleType type = VehicleType.valueOf((String) scan.get("detectedType"));
                    Map<String, Object> ticket = parkingService.checkIn(
                            type, plate, Boolean.TRUE.equals(scan.get("requiresCharging")));
                    response.put("autoEntry", true);
                    response.put("memberId", member.get().getMemberId());
                    response.put("memberName", member.get().getMemberName());
                    response.put("membershipType", member.get().getMembershipType().name());
                    response.put("membershipTypeDisplay", member.get().getMembershipType().getDisplayName());
                    response.put("membershipValidUntil", member.get().getValidUntil().toString());
                    response.put("ticket", ticket);
                    response.put("gateAction", "OPEN_ENTRY_GATE");
                    response.put("gateCommand", ticket.get("gateCommand"));
                } else {
                    response.put("gateAction", "MANUAL_CONFIRMATION_REQUIRED");
                    response.put("message", "ไม่พบสมาชิกที่ยังใช้งานได้ กรุณาตรวจสอบข้อมูลและกดยืนยันเข้าจอด");
                }

                sendJsonResponse(exchange, 200, response);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, Map.of("error", ex.getMessage()));
            }
        }
    }

    /** GET /api/hardware/status - สถานะอุปกรณ์จำลอง */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiHardwareStatusHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            sendJsonResponse(exchange, 200, parkingService.getHardwareStatus());
        }
    }

    /** POST /api/hardware/gate - Adapter endpoint สำหรับ OPEN/CLOSE ไม้กั้น */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiHardwareGateHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                Map<String, String> req = SimpleJson.parseSimpleJson(readRequestBody(exchange));
                GateLane lane = GateLane.valueOf(req.getOrDefault("lane", "ENTRY").toUpperCase());
                String action = req.getOrDefault("action", "CLOSE");
                sendJsonResponse(exchange, 200,
                        parkingService.controlGate(lane, action, req.getOrDefault("reason", "Web UI command")));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 400, Map.of("error", ex.getMessage()));
            }
        }
    }

    /**
     * Handler: GET /api/ai/predict
     * พยากรณ์อัตราความหนาแน่นและการปรับราคาแบบ Dynamic Pricing
     */
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiPredictHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiInsightsHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class ApiAiChatHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
    private class StaticFileHandler implements HttpHandler {
        @Override
        // [OOP: METHOD] Method handle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (platformOnly && (path == null || path.equals("/") || path.isEmpty()
                    || path.equals("/index.html") || path.equals("/login.html"))) {
                exchange.getResponseHeaders().set("Location", "/platform.html");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            if (path == null || path.equals("/") || path.isEmpty()) {
                if (!isAuthenticated(exchange)) {
                    redirectToLogin(exchange);
                    return;
                }
                path = "/index.html";
            }

            if ("/login.html".equals(path) && isAuthenticated(exchange)) {
                redirectToRoot(exchange);
                return;
            }

            if ("/index.html".equals(path) && !isAuthenticated(exchange)) {
                redirectToLogin(exchange);
                return;
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

    // [OOP: METHOD] Method redirectToLogin() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void redirectToLogin(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/login.html");
        exchange.sendResponseHeaders(302, -1);
    }

    // [OOP: METHOD] Method redirectToRoot() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void redirectToRoot(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(302, -1);
    }

    /**
     * วิเคราะห์และกำหนด MIME Content-Type ตามนามสกุลไฟล์
     * @param fileName ชื่อไฟล์ที่ต้องการตรวจสอบ
     * @return ค่า Content-Type เช่น text/html, text/css, application/javascript
     */
    // [OOP: METHOD] Method determineContentType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: METHOD] Method sendCors() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void sendCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin(exchange));
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
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
    // [OOP: METHOD] Method sendJsonResponse() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = SimpleJson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin(exchange));
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // [OOP: METHOD] Method allowedOrigin() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static String allowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) return "http://localhost:8080";
        if (origin.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?")) return origin;
        return "http://localhost:8080";
    }

    /**
     * อ่านข้อมูล Text Body ที่ส่งมาจาก HTTP Request (Payload) และแปลงเป็น String UTF-8
     * @param exchange อินสแตนซ์ HttpExchange
     * @return ข้อความสตริงทั้งหมดที่อยู่ใน Request Body
     * @throws IOException หากเกิดข้อผิดพลาดในการอ่าน InputStream
     */
    // [OOP: METHOD] Method readRequestBody() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
    // [OOP: METHOD] Method main() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
        f1.addSlot(SlotFactory.createSlot("F1-VIP", 1, SlotType.VIP));
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
        Path dataDir = Paths.get("data").toAbsolutePath();
        MembershipRepository membershipRepo = new MembershipRepository(dataDir.resolve("memberships.db"));
        ReservationRepository reservationRepo = new ReservationRepository(dataDir.resolve("reservations.db"));
        ParkingHistoryRepository historyRepo = new ParkingHistoryRepository(dataDir.resolve("parking-history.db"));
        DisplayBoard displayBoard = new DisplayBoard("BOARD-MAIN-GATE");

        ParkingService service = new ParkingService(lot, ticketRepo, paymentRepo, displayBoard,
                reservationRepo, membershipRepo, historyRepo);

        // 3. ใส่ประวัติรถเข้าออกจำลองกระจายย้อนหลัง 3 เดือน (ทำซ้ำได้โดยไม่เพิ่มรายการซ้ำ)
        seedDemoHistory(historyRepo);

        // 4. จำลองรถที่กำลังจอดอยู่เพื่อให้มีข้อมูลพร้อมทดสอบ
        seedDemoData(service);

        // 5. สตาร์ตเซิร์ฟเวอร์
        Path webDir = Paths.get("web").toAbsolutePath();
        ParkingServer server = new ParkingServer(service, webDir);
        server.start();
    }

    /**
     * ฟังก์ชันสำหรับใส่ข้อมูลจำลอง (Seed Demo Data)
     * จำลองเหตุการณ์รถเข้าจอดในช่วงเวลาต่าง ๆ เพื่อให้ระบบมีข้อมูลทดสอบที่สมจริง
     * @param service เซอร์วิสระบบที่ดูแลที่จอดรถ
     */
    // [OOP: METHOD] Method seedDemoData() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static void seedDemoData(ParkingService service) {
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

    /**
     * สร้างประวัติรถเข้าออกตัวอย่าง 30 รายการ กระจายตั้งแต่ 88 วันก่อนจนถึงเมื่อวาน
     * ใช้ Ticket ID คงที่จึงอัปเดตรายการเดิมเมื่อเปิด Server ใหม่ ไม่สร้างข้อมูลซ้ำ
     */
    // [OOP: METHOD] Seed ข้อมูลตัวอย่างสำหรับหน้าประวัติย้อนหลัง 3 เดือน
    private static void seedDemoHistory(ParkingHistoryRepository historyRepository) {
        String[] plates = {
                "1กก-1023", "2ขข-4587", "3คค-7712", "4งง-2098", "5จจ-6631",
                "6ฉฉ-8145", "7ชช-3902", "8ซซ-5476", "9ญญ-1258", "1ฎฎ-9364",
                "2ฏฏ-4071", "3ฐฐ-6829", "4ฑฑ-1537", "5ณณ-7480", "6ดด-2916",
                "7ตต-8653", "8ถถ-3149", "9ทท-5706", "1นน-4285", "2บบ-7931",
                "3ปป-2468", "4ผผ-9017", "5พพ-6354", "6ฟฟ-1729", "7มม-5842",
                "8ยย-3206", "9รร-7561", "1ลล-4893", "2วว-2175", "3สส-8430"
        };
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < plates.length; i++) {
            int daysAgo = 88 - (i * 3);
            LocalDateTime entry = now.minusDays(daysAgo)
                    .withHour(7 + (i % 10)).withMinute((i * 7) % 60).withSecond(0).withNano(0);
            int durationMinutes = 35 + (i % 6) * 25;
            LocalDateTime exit = entry.plusMinutes(durationMinutes);
            VehicleType type = switch (i % 4) {
                case 1 -> VehicleType.ELECTRIC_VEHICLE;
                case 2 -> VehicleType.MOTORCYCLE;
                case 3 -> VehicleType.TRUCK;
                default -> VehicleType.CAR;
            };
            int floor = type == VehicleType.MOTORCYCLE || type == VehicleType.TRUCK ? 3
                    : type == VehicleType.ELECTRIC_VEHICLE ? 1 : 2;
            String slot = switch (type) {
                case ELECTRIC_VEHICLE -> "F1-0" + (1 + (i % 3));
                case MOTORCYCLE -> "F3-0" + (1 + (i % 4));
                case TRUCK -> "F3-0" + (5 + (i % 2));
                default -> "F2-0" + (1 + (i % 8));
            };
            long hours = Math.max(1, (durationMinutes + 59L) / 60L);
            double fee = switch (type) {
                case MOTORCYCLE -> hours * 10.0;
                case ELECTRIC_VEHICLE -> hours * 40.0;
                case TRUCK -> hours * 50.0;
                default -> hours == 1 ? 20.0 : 20.0 + (hours - 1) * 30.0;
            };

            Ticket ticket = new Ticket(String.format("HIS-DEMO-%03d", i + 1), plates[i], type,
                    floor, slot, entry);
            historyRepository.recordEntry(ticket, entry);
            ticket.markPaid(fee, String.format("PAY-DEMO-%03d", i + 1), exit.minusMinutes(5));
            ticket.markExited(exit);
            historyRepository.recordExit(ticket, exit);
        }
        historyRepository.purgeExpired(now);
    }
}
