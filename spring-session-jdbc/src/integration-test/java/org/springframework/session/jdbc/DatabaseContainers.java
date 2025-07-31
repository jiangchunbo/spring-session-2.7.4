/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.jdbc;

import org.testcontainers.containers.Db2Container;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Factories for various {@link JdbcDatabaseContainer}s.
 *
 * @author Vedran Pavic
 */
final class DatabaseContainers {

	private DatabaseContainers() {
	}

	static Db2Container db2() {
		return new Db2Container("ibmcom/db2:11.5.4.0");
	}

	static MariaDBContainer<?> mariaDb() {
		return new MariaDBContainer<>("mariadb:10.7.3");
	}

	static MySQLContainer<?> mySql() {
		return new MySQLContainer<>("mysql:8.0.28");
	}

	static OracleContainer oracle() {
		return new OracleContainer("gvenzl/oracle-xe:18.4.0-slim");
	}

	static PostgreSQLContainer<?> postgreSql() {
		return new PostgreSQLContainer<>("postgres:14.2");
	}

	static MSSQLServerContainer<?> sqlServer() {
		return new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-CU8-ubuntu-16.04");
	}

}
