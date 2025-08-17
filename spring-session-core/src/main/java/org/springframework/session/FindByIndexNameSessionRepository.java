/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session;

import java.util.Map;

/**
 * Extends a basic {@link SessionRepository} to allow finding sessions by the specified
 * index name and index value.
 * <p>
 * 这是对 SessionRepository 进一层的强化，能够通过特定的 index name 和 index value 找到 session
 *
 * @param <S> the type of Session being managed by this
 *            {@link FindByIndexNameSessionRepository}
 * @author Rob Winch
 * @author Vedran Pavic
 */
public interface FindByIndexNameSessionRepository<S extends Session> extends SessionRepository<S> {

	/**
	 * A session index that contains the current principal name (i.e. username).
	 * <p>
	 * It is the responsibility of the developer to ensure the index is populated since
	 * Spring Session is not aware of the authentication mechanism being used.
	 *
	 * @since 1.1
	 */
	String PRINCIPAL_NAME_INDEX_NAME = FindByIndexNameSessionRepository.class.getName()
			.concat(".PRINCIPAL_NAME_INDEX_NAME");

	/**
	 * Find a {@link Map} of the session id to the {@link Session} of all sessions that
	 * contain the specified index name index value.
	 * <p>
	 * 获取一个 Map，其中 Key 是 Session ID，Value 是 Session 对象；
	 * <p>
	 * 这个 Map 中只包含满足“给定索引名称-索引值”条件的所有会话
	 * <p>
	 * 你就理解为 SQL -> select * from sessions where indexName = indexValue
	 *
	 * @param indexName  the name of the index (i.e.
	 *                   {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return a {@code Map} (never {@code null}) of the session id to the {@code Session}
	 * of all sessions that contain the specified index name and index value. If no
	 * results are found, an empty {@code Map} is returned.
	 */
	Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue);

	/**
	 * Find a {@link Map} of the session id to the {@link Session} of all sessions that
	 * contain the index with the name
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME} and the
	 * specified principal name.
	 * <p>
	 * 找到所有的会话 Map，其中属性 principal name 匹配传入的值
	 * <p>
	 * 其实 principal name 就是用户名
	 *
	 * @param principalName the principal name
	 * @return a {@code Map} (never {@code null}) of the session id to the {@code Session}
	 * of all sessions that contain the specified principal name. If no results are found,
	 * an empty {@code Map} is returned.
	 * @since 2.1.0
	 */
	default Map<String, S> findByPrincipalName(String principalName) {

		return findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, principalName);

	}

}
