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
package org.jdbi.v3.sqlobject;

import java.util.List;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.mapper.SomethingMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.FetchSize;
import org.jdbi.v3.sqlobject.customizer.MaxRows;
import org.jdbi.v3.sqlobject.customizer.QueryTimeOut;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_UNCOMMITTED;

public class TestModifiers {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withInitializer(TestingInitializers.something()).withPlugin(new SqlObjectPlugin());
    private Handle handle;

    @BeforeEach
    public void setUp() {
        handle = h2Extension.getSharedHandle();
        handle.registerRowMapper(new SomethingMapper());
    }

    @Test
    public void testFetchSizeAsArgOnlyUsefulWhenSteppingThroughDebuggerSadly() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAll(1);
        assertThat(things).hasSize(3);
    }

    @Test
    public void testFetchSizeOnMethodOnlyUsefulWhenSteppingThroughDebuggerSadly() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAll();
        assertThat(things).hasSize(3);
    }

    @Test
    public void testMaxSizeOnMethod() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAllWithMaxRows();
        assertThat(things).hasSize(1);
    }

    @Test
    public void testMaxSizeOnParam() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAllWithMaxRows(2);
        assertThat(things).hasSize(2);
    }

    @Test
    public void testQueryTimeOutOnMethodOnlyUsefulWhenSteppingThroughDebuggerSadly() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAllWithQueryTimeOut();
        assertThat(things).hasSize(3);
    }

    @Test
    public void testQueryTimeOutOnParamOnlyUsefulWhenSteppingThroughDebuggerSadly() {
        Spiffy s = handle.attach(Spiffy.class);
        s.insert(14, "Tom");
        s.insert(15, "JFA");
        s.insert(16, "Sunny");

        List<Something> things = s.findAllWithQueryTimeOut(2);
        assertThat(things).hasSize(3);
    }

    @Test
    public void testIsolationLevel() {
        try (Handle h1 = h2Extension.getJdbi().open();
            Handle h2 = h2Extension.getJdbi().open()) {
            Spiffy spiffy = h1.attach(Spiffy.class);
            IsoLevels iso = h2.attach(IsoLevels.class);

            spiffy.begin();
            spiffy.insert(1, "Tom");

            Something tom = iso.findById(1);
            assertThat(tom).isNotNull();

            spiffy.rollback();

            Something notTom = iso.findById(1);
            assertThat(notTom).isNull();
        }
    }

    @RegisterRowMapper(SomethingMapper.class)
    public interface Spiffy extends Transactional<Spiffy> {
        @SqlQuery("select id, name from something where id = :id")
        Something byId(@Bind("id") long id);

        @SqlQuery("select id, name from something")
        List<Something> findAll(@FetchSize(1) int fetchSize);

        @SqlQuery("select id, name from something")
        @FetchSize(2)
        List<Something> findAll();

        @SqlQuery("select id, name from something")
        List<Something> findAllWithMaxRows(@MaxRows int fetchSize);

        @SqlQuery("select id, name from something")
        @MaxRows(1)
        List<Something> findAllWithMaxRows();

        @SqlQuery("select id, name from something")
        List<Something> findAllWithQueryTimeOut(@QueryTimeOut(1) int timeOutInSeconds);

        @SqlQuery("select id, name from something")
        @QueryTimeOut(1)
        List<Something> findAllWithQueryTimeOut();

        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        void insert(@Bind("id") long id, @Bind("name") String name);
    }

    @RegisterRowMapper(SomethingMapper.class)
    public interface IsoLevels {
        @Transaction(READ_UNCOMMITTED)
        @SqlQuery("select id, name from something where id = :id")
        Something findById(@Bind("id") int id);
    }

}
