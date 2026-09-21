package platform;

import java.util.*;

/** OOP domain object: a versioned schematic grid. It does not certify physical turning clearance. */
public final class ParkingLayout {
    public static final Set<String> TYPES = Set.of("ROAD", "ENTRY", "EXIT", "SLOT", "BUILDING", "CROSSING", "CAMERA", "BARRIER", "SENSOR");
    public static final Set<String> SLOT_TYPES = Set.of("STANDARD", "EV_CHARGING", "MOTORCYCLE", "VIP", "ACCESSIBLE", "LARGE");
    private final List<Map<String,Object>> cells;
    public ParkingLayout(List<Map<String,Object>> input) {
        if (input.size() > 3072) throw new IllegalArgumentException("ผังใหญ่เกินกำหนด");
        Set<String> ids = new HashSet<>(), positions = new HashSet<>(), labels = new HashSet<>();
        cells = new ArrayList<>();
        for (Map<String,Object> c : input) {
            String id = PlatformService.string(c,"id"), type = PlatformService.string(c,"type");
            int x = PlatformService.integer(c,"x",0,23), y = PlatformService.integer(c,"y",0,15);
            int floor = PlatformService.integer(c,"floor",1,8), rotation = PlatformService.integer(c,"rotation",0,3);
            String label = PlatformService.string(c,"label");
            if (!TYPES.contains(type) || !ids.add(id) || !positions.add(floor+":"+x+":"+y)) throw new IllegalArgumentException("ชนิดหรือพิกัดซ้ำในผัง");
            String slotType = c.getOrDefault("slotType","STANDARD").toString();
            if (!SLOT_TYPES.contains(slotType)) throw new IllegalArgumentException("ประเภทช่องไม่ถูกต้อง");
            if (type.equals("SLOT") && !labels.add(label)) throw new IllegalArgumentException("หมายเลขช่องซ้ำ");
            cells.add(new LinkedHashMap<>(Map.of("id",id,"type",type,"x",x,"y",y,"floor",floor,
                "rotation",rotation,"label",label,"slotType",slotType,"oneWay",Boolean.TRUE.equals(c.get("oneWay")))));
        }
    }
    public List<Map<String,Object>> cells() { return cells; }
    private boolean road(Map<String,Object> c) { return Set.of("ROAD","ENTRY","EXIT","CROSSING","BARRIER").contains(c.get("type")); }
    private int n(Map<String,Object> c,String k) { return ((Number)c.get(k)).intValue(); }
    private boolean near(Map<String,Object> a, Map<String,Object> b) {
        return n(a,"floor")==n(b,"floor") && Math.abs(n(a,"x")-n(b,"x"))+Math.abs(n(a,"y")-n(b,"y"))==1;
    }
    private boolean edge(Map<String,Object> a, Map<String,Object> b) {
        if (!near(a,b)) return false;
        if (!Boolean.TRUE.equals(a.get("oneWay"))) return true;
        int[][] directions={{1,0},{0,1},{-1,0},{0,-1}}; int[] d=directions[n(a,"rotation")];
        return n(b,"x")-n(a,"x")==d[0] && n(b,"y")-n(a,"y")==d[1];
    }
    public List<String> route(String slotId) {
        Map<String,Object> slot=cells.stream().filter(c->c.get("id").equals(slotId)&&c.get("type").equals("SLOT")).findFirst().orElseThrow();
        List<Map<String,Object>> roads=cells.stream().filter(this::road).toList();
        Queue<Map<String,Object>> queue=new ArrayDeque<>(); Map<String,String> parent=new HashMap<>();
        for(var c:roads) if(c.get("type").equals("ENTRY")) { queue.add(c); parent.put(c.get("id").toString(),null); }
        while(!queue.isEmpty()) {
            var c=queue.remove(); String id=c.get("id").toString();
            if(near(c,slot)) { List<String> result=new ArrayList<>(); result.add(slotId); for(String p=id;p!=null;p=parent.get(p)) result.add(p); Collections.reverse(result); return result; }
            for(var b:roads) if(edge(c,b)&&!parent.containsKey(b.get("id").toString())) { parent.put(b.get("id").toString(),id); queue.add(b); }
        }
        return List.of();
    }
    public List<String> validate() {
        List<String> errors=new ArrayList<>();
        if(cells.stream().noneMatch(c->c.get("type").equals("SLOT"))) errors.add("ต้องมีช่องจอดอย่างน้อย 1 ช่อง");
        List<Map<String,Object>> roads=cells.stream().filter(this::road).toList();
        Set<String> canExit=new HashSet<>(); Queue<Map<String,Object>> q=new ArrayDeque<>();
        for(var c:roads) if(c.get("type").equals("EXIT")) { q.add(c); canExit.add(c.get("id").toString()); }
        while(!q.isEmpty()) { var c=q.remove(); for(var b:roads) if(edge(b,c)&&canExit.add(b.get("id").toString())) q.add(b); }
        Set<String> fromEntry=new HashSet<>();
        for(var c:roads) if(c.get("type").equals("ENTRY")) { q.add(c); fromEntry.add(c.get("id").toString()); }
        while(!q.isEmpty()) { var c=q.remove(); for(var b:roads) if(edge(c,b)&&fromEntry.add(b.get("id").toString())) q.add(b); }
        for(var c:cells) if(c.get("type").equals("SLOT")) {
            if(roads.stream().noneMatch(r->near(r,c)&&fromEntry.contains(r.get("id").toString()))) errors.add(c.get("label")+": ไม่มีเส้นทางจากทางเข้า");
            if(roads.stream().noneMatch(r->near(r,c)&&canExit.contains(r.get("id").toString()))) errors.add(c.get("label")+": ไปทางออกไม่ได้");
        }
        return errors;
    }
    public static List<Map<String,Object>> template(String businessType) {
        if(businessType.equals("CUSTOM")) return new ArrayList<>();
        List<Map<String,Object>> cells=new ArrayList<>();
        for(int x=0;x<24;x++) cells.add(cell("R"+x,x==0?"ENTRY":x==23?"EXIT":"ROAD",x,7,"ทางรถ", "STANDARD"));
        for(int x=2;x<22;x+=2) {
            cells.add(cell("A"+x,"SLOT",x,6,"A-"+x,x==2?"EV_CHARGING":"STANDARD"));
            cells.add(cell("B"+x,"SLOT",x,8,"B-"+x,x==2?"ACCESSIBLE":x==4?"MOTORCYCLE":"STANDARD"));
        }
        return cells;
    }
    private static Map<String,Object> cell(String id,String type,int x,int y,String label,String slotType) {
        return new LinkedHashMap<>(Map.of("id",id,"type",type,"x",x,"y",y,"floor",1,"rotation",0,"label",label,"slotType",slotType,"oneWay",false));
    }
}
