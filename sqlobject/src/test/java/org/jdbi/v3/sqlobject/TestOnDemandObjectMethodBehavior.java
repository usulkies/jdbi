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

import java.sql.Connection;

import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestOnDemandObjectMethodBehavior {
    private UselessDao dao;

    public interface UselessDao extends SqlObject {
        void finalize();
    }

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withPlugin(new SqlObjectPlugin());

    @BeforeEach
    public void setUp() {
        dao = h2Extension.attach(UselessDao.class);
    }

    /**
     * Sometimes the GC will call {@link #finalize()} on a SqlObject from
     * extremely sensitive places from within the GC machinery.  Jdbi should not
     * open a {@link Connection} just to satisfy a (no-op) finalizer.
     * <a href="https://github.com/brianm/jdbi/issues/82">Issue #82</a>.
     */
    @Test
    public void testFinalizeDoesntConnect() {
        dao.finalize(); // Normally GC would do this, but just fake it
    }

}
