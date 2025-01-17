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
package org.jdbi.v3.vavr;

import java.util.Objects;

import io.vavr.collection.Set;
import io.vavr.control.Option;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.NoSuchMapperException;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestVavrOptionMapperWithDB {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withInitializer(TestingInitializers.something()).installPlugins();

    @BeforeEach
    public void addData() {
        Handle handle = h2Extension.openHandle();
        handle.createUpdate("insert into something (id, name) values (1, 'eric')").execute();
        handle.createUpdate("insert into something (id) values (2)").execute();
    }

    @Test
    public void testOptionMappedShouldSucceed() {
        final Set<Option<String>> result = h2Extension.getSharedHandle()
            .createQuery("select name from something")
            .collectInto(new GenericType<Set<Option<String>>>() {});

        assertThat(result).hasSize(2);
        assertThat(result).contains(Option.none(), Option.of("eric"));
    }

    @Test
    public void testOptionMappedWithoutGenericParameterShouldFail() {
        assertThatThrownBy(() -> h2Extension.getSharedHandle()
            .registerRowMapper(ConstructorMapper.factory(SomethingWithOption.class))
            .createQuery("select name from something")
            .collectInto(new GenericType<Set<Option>>() {}))
                .isInstanceOf(NoSuchMapperException.class)
                .hasMessageContaining("raw");
    }

    @Test
    public void testOptionMappedWithoutNestedMapperShouldFail() {
        assertThatThrownBy(() -> h2Extension.getSharedHandle()
            .createQuery("select id, name from something")
            .collectInto(new GenericType<Set<Option<SomethingWithOption>>>() {}))
                .isInstanceOf(NoSuchMapperException.class)
                .hasMessageContaining("nested");
    }

    @Test
    public void testOptionMappedWithinObjectIfPresentShouldContainValue() {
        final SomethingWithOption result = h2Extension.getSharedHandle()
            .registerRowMapper(ConstructorMapper.factory(SomethingWithOption.class))
            .createQuery("select id, name from something where id = 1")
            .mapTo(SomethingWithOption.class)
            .one();

        assertThat(result.getName()).isInstanceOf(Option.class);
        assertThat(result).isEqualTo(new SomethingWithOption(1, Option.of("eric")));
    }

    @Test
    public void testOptionWithinObjectIfMissingShouldBeNone() {
        final SomethingWithOption result = h2Extension.getSharedHandle()
            .registerRowMapper(ConstructorMapper.factory(SomethingWithOption.class))
            .createQuery("select id, name from something where id = 2")
            .mapTo(SomethingWithOption.class)
            .one();

        assertThat(result.getName()).isInstanceOf(Option.class);
        assertThat(result).isEqualTo(new SomethingWithOption(2, Option.none()));
    }

    public static class SomethingWithOption {
        private int id;
        private Option<String> name;

        public SomethingWithOption(int id, Option<String> name) {
            this.id = id;
            this.name = name;
        }

        public Option<String> getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SomethingWithOption that = (SomethingWithOption) o;
            return id == that.id
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }

}
