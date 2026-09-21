package platform;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import util.SimpleJson;

/** Separate platform session prevents legacy single-lot demo accounts from gaining tenant permissions. */
public final class PlatformHandler implements HttpHandler {
    private record Session(String userId,Instant expires) {}
    private record Attempts(int count,long minute) {}
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,Attempts> attempts=new ConcurrentHashMap<>();
    private final PlatformService service;
    public PlatformHandler() throws IOException {
        try {
            String directory=System.getenv().getOrDefault("PLATFORM_DATA_DIR","data/platform");
            String databaseUrl=System.getenv("DATABASE_URL");
            PlatformStore store=databaseUrl==null||databaseUrl.isBlank()
                ? new FilePlatformStore(Path.of(directory,"platform.json"))
                : new PostgresPlatformStore(databaseUrl,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
            service=new PlatformService(store,"true".equalsIgnoreCase(System.getenv("PLATFORM_DEMO")),System.getenv("PLATFORM_ADMIN_PASSWORD"));
        } catch(Exception e) { throw new IOException("Cannot initialize platform store",e); }
    }
    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
            boolean write=method.equals("POST");
            if(!write&&!method.equals("GET")) { reply(exchange,405,Map.of("error","Method not allowed")); return; }
            if(path.equals("/api/platform/health")&&method.equals("GET")) {
                reply(exchange,200,Map.of("status","ok")); return;
            }
            if(write) {
                String origin=exchange.getRequestHeaders().getFirst("Origin"),host=exchange.getRequestHeaders().getFirst("Host");
                if(origin!=null&&!origin.equals("http://"+host)&&!origin.equals("https://"+host)) throw new SecurityException("Cross-origin request rejected");
                String type=exchange.getRequestHeaders().getFirst("Content-Type");
                if(type==null||!type.startsWith("application/json")) { reply(exchange,415,Map.of("error","Use application/json")); return; }
            }
            if(path.equals("/api/platform/login")&&write) {
                long minute=System.currentTimeMillis()/60000; String ip=exchange.getRemoteAddress().getAddress().getHostAddress();
                attempts.entrySet().removeIf(e->e.getValue().minute()!=minute);
                Attempts a=attempts.compute(ip,(k,old)->new Attempts(old==null||old.minute()!=minute?1:old.count()+1,minute));
                if(a.count()>12) { reply(exchange,429,Map.of("error","ลองเข้าสู่ระบบมากเกินไป รอ 1 นาที")); return; }
                var request=body(exchange); String user=service.login(PlatformService.string(request,"username"),PlatformService.string(request,"password"));
                sessions.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));
                if(sessions.size()>=1000) throw new IllegalStateException("Too many sessions");
                String token=UUID.randomUUID().toString()+UUID.randomUUID(); sessions.put(token,new Session(user,Instant.now().plusSeconds(28800)));
                exchange.getResponseHeaders().add("Set-Cookie","platform_session="+token+"; Path=/api/platform; HttpOnly; SameSite=Strict; Max-Age=28800"+secure());
                reply(exchange,200,service.view(user)); return;
            }
            String token=cookie(exchange); Session session=sessions.get(token);
            if(session==null||!session.expires().isAfter(Instant.now())) { sessions.remove(token); reply(exchange,401,Map.of("error","กรุณาเข้าสู่แพลตฟอร์ม")); return; }
            if(path.equals("/api/platform/state")&&method.equals("GET")) reply(exchange,200,service.view(session.userId()));
            else if(path.equals("/api/platform/command")&&write) reply(exchange,200,service.command(session.userId(),body(exchange)));
            else if(path.equals("/api/platform/logout")&&write) {
                sessions.remove(token); exchange.getResponseHeaders().add("Set-Cookie","platform_session=; Path=/api/platform; HttpOnly; SameSite=Strict; Max-Age=0"+secure()); reply(exchange,200,Map.of("ok",true));
            } else reply(exchange,404,Map.of("error","Not found"));
        } catch(SecurityException e) { reply(exchange,403,Map.of("error",e.getMessage())); }
          catch(ConcurrentModificationException e) { reply(exchange,409,Map.of("error",e.getMessage())); }
          catch(IllegalArgumentException|NoSuchElementException e) { reply(exchange,400,Map.of("error",e.getMessage()==null?"ข้อมูลไม่ถูกต้อง":e.getMessage())); }
          catch(Exception e) { System.err.println("Platform request failed: "+e.getClass().getSimpleName()); reply(exchange,500,Map.of("error","บันทึกไม่สำเร็จ ข้อมูลเดิมยังอยู่ กรุณาลองใหม่")); }
    }
    private String secure() { return "true".equalsIgnoreCase(System.getenv("PLATFORM_SECURE_COOKIE"))?"; Secure":""; }
    private String cookie(HttpExchange exchange) {
        for(String header:exchange.getRequestHeaders().getOrDefault("Cookie",List.of())) for(String p:header.split(";")) if(p.trim().startsWith("platform_session=")) return p.trim().substring(17);
        return "";
    }
    private Map<String,Object> body(HttpExchange exchange) throws IOException {
        byte[] bytes=exchange.getRequestBody().readNBytes(1024*1024+1);
        if(bytes.length>1024*1024) throw new IllegalArgumentException("Request exceeds 1 MiB");
        return PlatformService.map(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
    }
    private void reply(HttpExchange exchange,int status,Object body) throws IOException {
        byte[] bytes=SimpleJson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.sendResponseHeaders(status,bytes.length); try(var out=exchange.getResponseBody()) { out.write(bytes); }
    }
}
