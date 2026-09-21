package platform;

/** Persistence boundary for the platform aggregate. */
public interface PlatformStore {
    default <T> T transaction(java.util.concurrent.Callable<T> action) throws Exception { return action.call(); }
    String load() throws Exception;
    void save(String json) throws Exception;
    String description();
}
