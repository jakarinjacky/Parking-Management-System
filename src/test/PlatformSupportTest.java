package test;
import platform.*;
import java.util.*;
import static platform.PlatformService.*;
import static test.PlatformAnalyticsTest.*;
import static test.PlatformTenantLifecycleTest.denied;

public class PlatformSupportTest {
    public static void main(String[] args)throws Exception {
        Store store=new Store();var s=new PlatformService(store,false,"Administrator123!");String root=s.login("superadmin","Administrator123!");
        for(String name:List.of("customer","other"))cmd(s,root,"createTenant",Map.of("name",name,"username",name,"password","CustomerPass123!"));
        String owner=s.login("customer","CustomerPass123!"),other=s.login("other","CustomerPass123!");
        cmd(s,owner,"createSupportTicket",Map.of("subject","Problem <script>","message","Steps\nDetails"));
        String id=list(s.view(owner),"supportTickets").get(0).get("id").toString();
        check(list(s.view(other),"supportTickets").isEmpty());check(list(s.view(root),"supportTickets").size()==1);
        denied(()->cmd(s,other,"replySupportTicket",Map.of("ticketId",id,"message","cross tenant")));
        denied(()->cmd(s,owner,"setSupportStatus",Map.of("ticketId",id,"status","RESOLVED")));
        denied(()->cmd(s,owner,"createSupportTicket",Map.of("subject","Empty","message"," ")));
        cmd(s,root,"replySupportTicket",Map.of("ticketId",id,"message","Investigating"));
        cmd(s,root,"setSupportStatus",Map.of("ticketId",id,"status","RESOLVED"));
        cmd(s,owner,"replySupportTicket",Map.of("ticketId",id,"message","Still failing"));
        var ticket=list(s.view(owner),"supportTickets").get(0);check("OPEN".equals(ticket.get("status")));check(list(ticket,"messages").size()==3);
        var restarted=new PlatformService(store,false,null);check(list(list(restarted.view(root),"supportTickets").get(0),"messages").size()==3);
        String tenant=map(s.view(owner).get("user")).get("tenantId").toString();cmd(s,root,"setTenantActive",Map.of("tenantId",tenant,"active",false));
        cmd(s,root,"deleteTenant",Map.of("tenantId",tenant,"confirmationName","customer","currentPassword","Administrator123!"));check(list(s.view(root),"supportTickets").isEmpty());
        System.out.println("Support PASS: tenant isolation, roles, message validation, replies, reopen, restart and customer deletion");
    }
}
