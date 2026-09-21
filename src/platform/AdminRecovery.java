package platform;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;

/** Console-only recovery; never accepts passwords in arguments, logs or HTTP. */
public final class AdminRecovery {
    public static void main(String[] args) throws Exception {
        Console console=System.console();
        if(console==null) throw new IllegalStateException("Interactive terminal required");
        console.printf("Stop ALL app containers and back up the database before continuing.%n");
        if(!"RESET superadmin".equals(console.readLine("Type RESET superadmin: "))) return;
        char[] first=console.readPassword("New password (12–128 characters): ");
        char[] second=console.readPassword("Repeat password: ");
        try {
            if(first==null||second==null||!Arrays.equals(first,second)||first.length<12||first.length>128)
                throw new IllegalArgumentException("Passwords differ or length is invalid");
            String url=System.getenv("DATABASE_URL");
            PlatformStore store=url==null||url.isBlank()
                ? new FilePlatformStore(Path.of(System.getenv().getOrDefault("PLATFORM_DATA_DIR","data/platform"),"platform.json"))
                : new PostgresPlatformStore(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
            if(store.load()==null) throw new IllegalStateException("No existing data; use initial setup instead");
            new PlatformService(store,false,null).recoverAdmin(new String(first));
            console.printf("Password reset. Business data preserved. Start the app again.%n");
        } finally { if(first!=null) Arrays.fill(first,'\0'); if(second!=null) Arrays.fill(second,'\0'); }
    }
}
