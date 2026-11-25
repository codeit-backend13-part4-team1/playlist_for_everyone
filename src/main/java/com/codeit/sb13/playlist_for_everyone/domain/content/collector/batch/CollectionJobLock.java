package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class CollectionJobLock {

    private final DataSource dataSource;

    public CollectionJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 잠금을 얻으면 연결과 함께 보관 */
    public Optional<Handle> tryAcquire(long key) throws SQLException {
        Connection connection = dataSource.getConnection();
        boolean acquired = false;

        try {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_try_advisory_lock(?)"
            )) {
                statement.setLong(1, key);

                try (var result = statement.executeQuery()) {
                    acquired = result.next() && result.getBoolean(1);
                }
            }

            if (!acquired) {
                connection.close();
                return Optional.empty();
            }

            return Optional.of(new Handle(connection, key));
        } catch (SQLException | RuntimeException exception) {
            try (connection) {
                try {
                    connection.abort(Runnable::run);
                } catch (SQLException | RuntimeException abortException) {
                    exception.addSuppressed(abortException);
                }

                throw exception;
            }
        }
    }

    public static final class Handle implements AutoCloseable {

        private final Connection connection;
        private final long key;
        private boolean closed;

        private Handle(Connection connection, long key) {
            this.connection = connection;
            this.key = key;
        }

        /** 잠금 해제 후 연결 반납 */
        @Override
        public synchronized void close() throws SQLException {
            if (closed) {
                return;
            }

            closed = true;

            try (connection) {
                try (var statement = connection.prepareStatement(
                        "SELECT pg_advisory_unlock(?)"
                )) {
                    statement.setLong(1, key);

                    try (var result = statement.executeQuery()) {
                        if (!result.next() || !result.getBoolean(1)) {
                            throw new SQLException("수집 작업 잠금을 해제하지 못했습니다.");
                        }
                    }
                } catch (SQLException exception) {
                    try {
                        connection.abort(Runnable::run);
                    } catch (SQLException abortException) {
                        exception.addSuppressed(abortException);
                    }

                    throw exception;
                }
            }
        }
    }
}