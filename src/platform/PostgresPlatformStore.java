package platform;

import java.sql.*;

/** PostgreSQL-backed store. JDBC driver is supplied by the runtime container. */
public final class PostgresPlatformStore implements PlatformStore {
    private final String url;
    private final String user;
    private final String password;

    public PostgresPlatformStore(String url, String user, String password) throws Exception {
        if (url == null || !url.startsWith("jdbc:postgresql://"))
            throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL JDBC URL");
        this.url = url;
        this.user = user;
        this.password = password;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS platform_state (" +
                "id SMALLINT PRIMARY KEY CHECK (id = 1), " +
                "state JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override public String load() throws Exception {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT state::text FROM platform_state WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    @Override public void save(String json) throws Exception {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO platform_state (id, state) VALUES (1, ?::jsonb) " +
                 "ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state, updated_at = NOW()")) {
            statement.setString(1, json);
            statement.executeUpdate();
        }
    }

    @Override public String description() { return "postgresql-jsonb"; }
}
