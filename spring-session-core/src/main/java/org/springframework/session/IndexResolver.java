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
 * Strategy interface for resolving the {@link Session}'s indexes.
 * <p>
 * 一个策略接口，用于从 Session 中提取一组索引键值对。默认就是为了找到本次会话的用户名是谁。
 *
 * @param <S> the type of Session being handled
 * @author Rob Winch
 * @author Vedran Pavic
 * @see FindByIndexNameSessionRepository
 * @since 2.2.0
 */
public interface IndexResolver<S extends Session> {

	/**
	 * Resolve indexes for the session.
	 * <p>
	 * 从 Session 对象中提取一组[索引键值对]
	 *
	 * @param session the session
	 * @return a map of resolved indexes, never {@code null}
	 */
	Map<String, String> resolveIndexesFor(S session);

}
