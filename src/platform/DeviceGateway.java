package platform;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Authenticated telemetry + LED bench command only. No barrier-opening capability. */
public final class DeviceGateway {
    private DeviceGateway() {}
    public static String provision(Map<String,Object> device) {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=HexFormat.of().formatHex(bytes);
        device.put("tokenHash",digest(token)); device.put("mode","BENCH");
        device.remove("lastSeen"); device.remove("ledCommand"); return token;
    }
    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static Map<String,Object> event(Map<String,Object> device,String token,Map<String,Object> request) {
        String stored=device.getOrDefault("tokenHash","").toString();
        if(token==null||token.length()!=64||stored.isBlank()||!MessageDigest.isEqual(digest(token).getBytes(StandardCharsets.UTF_8),stored.getBytes(StandardCharsets.UTF_8))) throw new SecurityException("Device authentication failed");
        String type=PlatformService.string(request,"type");
        if(!Set.of("HEARTBEAT","PLATE","ACK").contains(type)) throw new IllegalArgumentException("Unsupported device event");
        String at=Instant.now().toString();
        if(type.equals("PLATE")) device.put("lastPlate",Map.of("plate",PlatformService.string(request,"plate"),"at",at));
        var pending=device.get("ledCommand") instanceof Map?PlatformService.map(device.get("ledCommand")):null;
        if(type.equals("ACK")) {
            if(pending==null||!pending.get("id").equals(PlatformService.string(request,"commandId"))) throw new IllegalArgumentException("Unknown command");
            if(Instant.parse(pending.get("expires").toString()).isBefore(Instant.now())) throw new IllegalArgumentException("Command expired");
            pending.put("status","ACKNOWLEDGED");
        }
        device.put("lastSeen",at);
        boolean available=pending!=null&&pending.get("status").equals("PENDING")&&Instant.parse(pending.get("expires").toString()).isAfter(Instant.now());
        return Map.of("ok",true,"serverTime",at,"command",available?pending:Map.of());
    }
    public static void queueLed(Map<String,Object> device) {
        if(!device.getOrDefault("mode","").equals("BENCH")||!device.get("type").equals("ESP32")||device.getOrDefault("tokenHash","").toString().isBlank()) throw new IllegalArgumentException("Provision an ESP32 bench device first");
        var old=device.get("ledCommand") instanceof Map?PlatformService.map(device.get("ledCommand")):null;
        if(old!=null&&old.get("status").equals("PENDING")&&Instant.parse(old.get("expires").toString()).isAfter(Instant.now())) throw new IllegalArgumentException("A command is already pending");
        device.put("ledCommand",new LinkedHashMap<>(Map.of("id",UUID.randomUUID().toString(),"type","LED_PULSE","durationMs",500,"expires",Instant.now().plusSeconds(60).toString(),"status","PENDING")));
    }
    public static Map<String,Object> publicView(Map<String,Object> device) {
        var copy=new LinkedHashMap<>(device); copy.remove("tokenHash");
        String last=copy.getOrDefault("lastSeen","").toString();
        copy.put("status",last.isEmpty()?"NOT_CONNECTED":Instant.parse(last).isAfter(Instant.now().minusSeconds(90))?"ONLINE":"OFFLINE");
        return copy;
    }
}
