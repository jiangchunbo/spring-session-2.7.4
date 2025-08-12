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

package org.springframework.session.web.http;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Contract for session id resolution strategies. Allows for session id resolution through
 * the request and for sending the session id or expiring the session through the
 * response.
 * <p>
 * 这是一种契约。允许通过请求解析到 Session ID，或者通过响应发送 Session ID 以及过期 Session。
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 2.0.0
 */
public interface HttpSessionIdResolver {

	/**
	 * Resolve the session ids associated with the provided {@link HttpServletRequest}.
	 * For example, the session id might come from a cookie or a request header.
	 * <p>
	 * 给定一个 Servlet 规范的请求，解析 Session ID。
	 * <p>
	 * 可能来自于一个 Cookie，经典的方法，也可能来自于一个请求头等等。
	 *
	 * @param request the current request
	 * @return the session ids
	 */
	List<String> resolveSessionIds(HttpServletRequest request);

	/**
	 * Send the given session id to the client. This method is invoked when a new session
	 * is created and should inform a client what the new session id is. For example, it
	 * might create a new cookie with the session id in it or set an HTTP response header
	 * with the value of the new session id.
	 * <p>
	 * 发送 Session ID 给客户端。当新的会话创建时，用来告知客户端新的 Session ID。
	 * <p>
	 * 例如，可以创建一个包含该 Session ID 的 Cookie，或者在 HTTP 响应头中设置该 Session ID 的值。
	 *
	 * @param request   the current request
	 * @param response  the current response
	 * @param sessionId the session id
	 */
	void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId);

	/**
	 * Instruct the client to end the current session. This method is invoked when a
	 * session is invalidated and should inform a client that the session id is no longer
	 * valid. For example, it might remove a cookie with the session id in it or set an
	 * HTTP response header with an empty value indicating to the client to no longer
	 * submit that session id.
	 * <p>
	 * 通知客户端结束当前会话。当服务器判定一个会话已经失效时，需要调用此方法告知客户端该会话 ID 已经不再有效。
	 * <p>
	 * 具体做法可以包括：删除存储 Session ID 的 Cookie，或者在 HTTP 响应头中设置一个空值，提示客户端不要再发送这个会话 ID
	 *
	 * @param request  the current request
	 * @param response the current response
	 */
	void expireSession(HttpServletRequest request, HttpServletResponse response);

}
