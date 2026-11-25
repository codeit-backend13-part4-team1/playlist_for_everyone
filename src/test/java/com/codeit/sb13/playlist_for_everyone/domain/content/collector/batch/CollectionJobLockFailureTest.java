package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionJobLockFailureTest {

    @ParameterizedTest
    @ValueSource(strings = {"resultSet", "statement"})
    void 잠금_획득_후_자원_종료가_실패하면_연결을_종료한다(String resourceType) throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        var failure = new SQLException("자원 종료 실패");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);

        if ("resultSet".equals(resourceType)) {
            doThrow(failure).when(result).close();
        } else {
            doThrow(failure).when(statement).close();
        }

        var lock = new CollectionJobLock(dataSource);

        assertThatThrownBy(() -> lock.tryAcquire(254_001L))
                .isSameAs(failure);

        verify(connection).abort(any());
        verify(connection).close();
    }
}