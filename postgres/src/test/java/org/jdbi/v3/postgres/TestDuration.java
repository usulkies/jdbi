/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.postgres;

import java.time.Duration;
import java.util.List;

import com.google.common.collect.ImmutableList;
import de.softwareforge.testing.postgres.junit5.EmbeddedPgExtension;
import de.softwareforge.testing.postgres.junit5.MultiDatabaseBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestDuration {

    @RegisterExtension
    public static EmbeddedPgExtension pg = MultiDatabaseBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public JdbiExtension pgExtension = JdbiExtension.postgres(pg).withPlugins(new SqlObjectPlugin(), new PostgresPlugin())
        .withInitializer((ds, h) -> h.useTransaction(th -> {
            th.execute("drop table if exists intervals");
            th.execute("create table intervals(id int not null, foo interval)");

            // Can be durations.
            th.execute("insert into intervals(id, foo) values(1, interval '1 day 15:00:00')");
            th.execute("insert into intervals(id, foo) values(2, interval '40 days 22 minutes')");

            // Can't be.
            th.execute("insert into intervals(id, foo) values(3, interval '10 years -3 months 100 seconds')");
        }));

    private Handle handle;

    private final Duration testDuration = Duration.ofDays(39).plusHours(23).plusMinutes(59).plusSeconds(1);

    @BeforeEach
    public void setUp() {
        handle = pgExtension.openHandle();
    }

    @Test
    public void testReadsViaFluentAPI() {
        List<Duration> periods = handle.createQuery("select foo from intervals where id = 1 or id = 2 order by id")
                .mapTo(Duration.class)
                .list();

        assertThat(periods).isEqualTo(ImmutableList.of(
                Duration.ofDays(1).plusHours(15),
                Duration.ofDays(40).plusMinutes(22)
       ));
    }

    @Test
    public void testTrivialDuration() {
        handle.execute("insert into intervals(id, foo) values(?, ?)", 4, Duration.ZERO);
        Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 4)
                .mapTo(Duration.class)
                .one();
        assertThat(d.isZero());
    }

    @Test
    public void testHandlesNulls() {
        handle.execute("insert into intervals(id, foo) values(?, ?)", 5, null);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 5)
                .mapTo(Duration.class)
                .one();
        assertThat(d).isNull();
    }

    @Test
    public void testWritesViaFluentApi() {
        handle.execute("insert into intervals(id, foo) values(?, ?)", 6, testDuration);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 6)
                .mapTo(Duration.class)
                .one();
        assertThat(d).isEqualTo(testDuration);
    }

    @Test
    public void testInvalidDuration() {
        assertThatThrownBy(() -> handle.createQuery("select foo from intervals where id=?")
            .bind(0, 3) // The bad one.
            .mapTo(Duration.class)
            .one()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testReadNegativeDuration() {
        handle.execute("insert into intervals(id, foo) values(?, interval '-2 days -3 hours')", 7);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 7)
                .mapTo(Duration.class)
                .one();
        assertThat(d).isEqualTo(Duration.ofDays(-2).plusHours(-3));
    }

    @Test
    public void testWriteReadNegativeDuration() {
        handle.execute("insert into intervals(id, foo) values(?, ?)",
                8, Duration.ofDays(-3).plusMinutes(2));
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 8)
                .mapTo(Duration.class)
                .one();
        assertThat(d).isEqualTo(Duration.ofDays(-3).plusMinutes(2));
    }

    @Test
    public void testWriteDurationTooBig() {
        assertThatThrownBy(() -> handle.execute("insert into intervals(id, foo) values(?, ?)", 9, Duration.ofDays((long) Integer.MAX_VALUE + 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * This is admittedly a test of an implementation detail, but this detail is documented in
     * {@link DurationArgumentFactory}, so this test failing is a good signal to update the documentation there.
     */
    @Test
    public void testWriteDurationTooSmall() {
        assertThatThrownBy(() -> handle.execute("insert into intervals(id, foo) values(?, ?)",
            10, Duration.ofSeconds(Long.MIN_VALUE))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void testTinyDuration() {
        handle.execute("insert into intervals(id, foo) values(?, interval '13us')", 11);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 11)
                .mapTo(Duration.class)
                .one();
        assertThat(d).isEqualTo(Duration.ofNanos(13_000));
    }

    @Test
    public void testDurationTooPrecise() {
        assertThatThrownBy(() -> handle.execute("insert into intervals(id, foo) values(?, ?)", 12, Duration.ofNanos(100)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFloatingPointMath() {
        handle.execute("insert into intervals(id, foo) values(?, interval '.129428s')", 13);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
            .bind(0, 13)
            .mapTo(Duration.class)
            .one();
        assertThat(d).isEqualTo(Duration.ofNanos(129_428_000));
    }

    // We guard against reading intervals with seconds too big or too small (i.e., more extreme than Long minimum and
    // maximum values), but it's unclear how to actually create such intervals in Postgres.
}
