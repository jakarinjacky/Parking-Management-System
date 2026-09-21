package platform;

/** Persistence boundary for the platform aggregate. */
public interface PlatformStore {
    String load() throws Exception;
    void save(String json) throws Exception;
    String description();
}
