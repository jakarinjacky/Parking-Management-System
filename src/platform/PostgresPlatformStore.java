package platform;

import java.sql.*;

/** PostgreSQL-backed store. JDBC driver is supplied by the runtime container. */
public final class PostgresPlatformStore implements PlatformStore {
    private final String url;
    private final String user;
    private final String password;
    private final ThreadLocal<Connection> current=new ThreadLocal<>();

    public PostgresPlatformStore(String url, String user, String password) throws Exception {
        if (url == null || !url.startsWith("jdbc:postgresql://"))
            throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL JDBC URL");
        this.url = url;
        this.user = user;
        this.password = password;
        transaction(()->{ try (Statement statement = current.get().createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS platform_state (" +
                "id SMALLINT PRIMARY KEY CHECK (id = 1), " +
                "state JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())");
        } return null; });
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    @Override public <T> T transaction(java.util.concurrent.Callable<T> action) throws Exception {
        if(current.get()!=null) return action.call();
        try(Connection c=connect()) {
            c.setAutoCommit(false); current.set(c);
            try {
                try(var s=c.createStatement()) {
                    s.execute("SET LOCAL lock_timeout='10s'");
                    s.execute("SET LOCAL statement_timeout='20s'");
                    s.execute("SELECT pg_advisory_xact_lock(7061726)");
                }
                T result=action.call(); c.commit(); return result;
            } catch(Exception e) { c.rollback(); throw e; }
            finally { current.remove(); }
        }
    }

    @Override public String load() throws Exception {
        return transaction(()->{ try (
             PreparedStatement statement = current.get().prepareStatement("SELECT state::text FROM platform_state WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        } });
    }

    @Override public void save(String json) throws Exception {
        transaction(()->{ try (
             PreparedStatement statement = current.get().prepareStatement(
                 "INSERT INTO platform_state (id, state) VALUES (1, ?::jsonb) " +
                 "ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state, updated_at = NOW()")) {
            statement.setString(1, json);
            statement.executeUpdate();
        } return null; });
    }

    @Override public String description() { return "postgresql-jsonb-transactional"; }
}
