/*
 * Copyright 2014-2021 the original author or authors.
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.Order;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * Switches the {@link javax.servlet.http.HttpSession} implementation to be backed by a
 * {@link org.springframework.session.Session}.
 * <p>
 * The {@link SessionRepositoryFilter} wraps the
 * {@link javax.servlet.http.HttpServletRequest} and overrides the methods to get an
 * {@link javax.servlet.http.HttpSession} to be backed by a
 * {@link org.springframework.session.Session} returned by the
 * {@link org.springframework.session.SessionRepository}.
 * <p>
 * The {@link SessionRepositoryFilter} uses a {@link HttpSessionIdResolver} (default
 * {@link CookieHttpSessionIdResolver}) to bridge logic between an
 * {@link javax.servlet.http.HttpSession} and the
 * {@link org.springframework.session.Session} abstraction. Specifically:
 *
 * <ul>
 * <li>The session id is looked up using
 * {@link HttpSessionIdResolver#resolveSessionIds(javax.servlet.http.HttpServletRequest)}
 * . The default is to look in a cookie named SESSION.</li>
 * <li>The session id of newly created {@link org.springframework.session.Session} is sent
 * to the client using
 * {@link HttpSessionIdResolver#setSessionId(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, String)}
 * <li>The client is notified that the session id is no longer valid with
 * {@link HttpSessionIdResolver#expireSession(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
 * </li>
 * </ul>
 *
 * <p>
 * The SessionRepositoryFilter must be placed before any Filter that access the
 * HttpSession or that might commit the response to ensure the session is overridden and
 * persisted properly.
 * </p>
 *
 * @param <S> the {@link Session} type.
 * @author Rob Winch
 * @author Vedran Pavic
 * @author Josh Cummings
 * @since 1.0
 */
@Order(SessionRepositoryFilter.DEFAULT_ORDER)
public class SessionRepositoryFilter<S extends Session> extends OncePerRequestFilter {

	private static final String SESSION_LOGGER_NAME = SessionRepositoryFilter.class.getName().concat(".SESSION_LOGGER");

	private static final Log SESSION_LOGGER = LogFactory.getLog(SESSION_LOGGER_NAME);

	/**
	 * The session repository request attribute name.
	 */
	public static final String SESSION_REPOSITORY_ATTR = SessionRepository.class.getName();

	/**
	 * Invalid session id (not backed by the session repository) request attribute name.
	 */
	public static final String INVALID_SESSION_ID_ATTR = SESSION_REPOSITORY_ATTR + ".invalidSessionId";

	/**
	 * 一个特殊的属性名，通过这个属性名，可以找到 ServletRequest 中的 HttpSessionWrapper 对象
	 */
	private static final String CURRENT_SESSION_ATTR = SESSION_REPOSITORY_ATTR + ".CURRENT_SESSION";

	/**
	 * The default filter order.
	 */
	public static final int DEFAULT_ORDER = Integer.MIN_VALUE + 50;

	/**
	 * Session 存储库/仓库。存储 Session 的抽象
	 */
	private final SessionRepository<S> sessionRepository;

	private HttpSessionIdResolver httpSessionIdResolver = new CookieHttpSessionIdResolver();

	/**
	 * Creates a new instance.
	 *
	 * @param sessionRepository the <code>SessionRepository</code> to use. Cannot be null.
	 */
	public SessionRepositoryFilter(SessionRepository<S> sessionRepository) {
		if (sessionRepository == null) {
			throw new IllegalArgumentException("sessionRepository cannot be null");
		}
		this.sessionRepository = sessionRepository;
	}

	/**
	 * Sets the {@link HttpSessionIdResolver} to be used. The default is a
	 * {@link CookieHttpSessionIdResolver}.
	 *
	 * @param httpSessionIdResolver the {@link HttpSessionIdResolver} to use. Cannot be
	 *                              null.
	 */
	public void setHttpSessionIdResolver(HttpSessionIdResolver httpSessionIdResolver) {
		if (httpSessionIdResolver == null) {
			throw new IllegalArgumentException("httpSessionIdResolver cannot be null");
		}
		this.httpSessionIdResolver = httpSessionIdResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 将 SessionRepository 设置到 request attribute 中
		// 可能是给后面其他请求处理使用吧，本项目倒是没有看到有什么用
		request.setAttribute(SESSION_REPOSITORY_ATTR, this.sessionRepository);

		// 包装 Request Response
		SessionRepositoryRequestWrapper wrappedRequest = new SessionRepositoryRequestWrapper(request, response);
		SessionRepositoryResponseWrapper wrappedResponse = new SessionRepositoryResponseWrapper(wrappedRequest, response);

		try {
			// 继续执行 filter chain
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			// 提交会话
			// 每次请求执行结束，都会 commit session (finally)
			wrappedRequest.commitSession();
		}
	}

	@Override
	protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
											   FilterChain filterChain) throws ServletException, IOException {
		doFilterInternal(request, response, filterChain);
	}

	/**
	 * Allows ensuring that the session is saved if the response is committed.
	 *
	 * @author Rob Winch
	 * @since 1.0
	 */
	private final class SessionRepositoryResponseWrapper extends OnCommittedResponseWrapper {

		private final SessionRepositoryRequestWrapper request;

		/**
		 * Create a new {@link SessionRepositoryResponseWrapper}.
		 *
		 * @param request  the request to be wrapped
		 * @param response the response to be wrapped
		 */
		SessionRepositoryResponseWrapper(SessionRepositoryRequestWrapper request, HttpServletResponse response) {
			super(response);
			if (request == null) {
				throw new IllegalArgumentException("request cannot be null");
			}
			this.request = request;
		}

		@Override
		protected void onResponseCommitted() {
			this.request.commitSession();
		}

	}

	/**
	 * A {@link javax.servlet.http.HttpServletRequest} that retrieves the
	 * {@link javax.servlet.http.HttpSession} using a
	 * {@link org.springframework.session.SessionRepository}.
	 *
	 * @author Rob Winch
	 * @since 1.0
	 */
	private final class SessionRepositoryRequestWrapper extends HttpServletRequestWrapper {

		private final HttpServletResponse response;

		/**
		 * 从本次请求中解析到的 Session 对象
		 */
		private S requestedSession;

		/**
		 * 用于控制是否需要解析 Session 对象。
		 * <p>
		 * 因为 S 未必可以解析到，所以不可能以 S 是否为 null 作为已经解析过的依据，所以增加此字段来控制。
		 * <p>
		 * 可以将该字段置为 false，重新进行解析。
		 */
		private boolean requestedSessionCached;

		/**
		 * 可能是无效的会话 ID，也可能是有效的 ID，具体还是看到底能否通过这个从 SessionRepository 找到会话对象
		 */
		private String requestedSessionId;

		/**
		 * 请求会话 ID 是否有效。默认是 null，表示还未设置。
		 */
		private Boolean requestedSessionIdValid;

		private boolean requestedSessionInvalidated;

		private SessionRepositoryRequestWrapper(HttpServletRequest request, HttpServletResponse response) {
			super(request);
			this.response = response;
		}

		/**
		 * Uses the {@link HttpSessionIdResolver} to write the session id to the response
		 * and persist the Session.
		 */
		private void commitSession() {
			// 获取当前 session
			HttpSessionWrapper wrappedSession = getCurrentSession();

			// 如果当前没有 session
			if (wrappedSession == null) {
				// 再次判断如果当前 session 是 null，并且 requestedSessionInvalidated 是 true
				if (isInvalidateClientSession()) {
					// 使用 HttpSessionIdResolver(很重要) 执行其 expireSession
					SessionRepositoryFilter.this.httpSessionIdResolver.expireSession(this, this.response);
				}
			} else {
				// 获取其中的 session
				S session = wrappedSession.getSession();

				clearRequestedSessionCache();

				// 保存到仓库中
				SessionRepositoryFilter.this.sessionRepository.save(session);
				String sessionId = session.getId();
				if (!isRequestedSessionIdValid() || !sessionId.equals(getRequestedSessionId())) {
					SessionRepositoryFilter.this.httpSessionIdResolver.setSessionId(this, this.response, sessionId);
				}
			}
		}

		/**
		 * 获取当前会话。从 request attribute 中获取
		 */
		@SuppressWarnings("unchecked")
		private HttpSessionWrapper getCurrentSession() {
			return (HttpSessionWrapper) getAttribute(CURRENT_SESSION_ATTR);
		}

		/**
		 * 设置当前会话
		 * <p>
		 * 这个属性的作用，你可以理解为本地缓存与远程缓存
		 */
		private void setCurrentSession(HttpSessionWrapper currentSession) {
			if (currentSession == null) {
				removeAttribute(CURRENT_SESSION_ATTR);
			} else {
				setAttribute(CURRENT_SESSION_ATTR, currentSession);
			}
		}

		@Override
		@SuppressWarnings("unused")
		public String changeSessionId() {
			HttpSession session = getSession(false);

			if (session == null) {
				throw new IllegalStateException(
						"Cannot change session ID. There is no session associated with this request.");
			}

			// 获取 Spring Session 调用 changeSessionId
			return getCurrentSession().getSession().changeSessionId();
		}

		/**
		 * 请求的会话 ID 是否有效。
		 * <p>
		 * 这个方法里面主要还是依靠 requestedSessionIdValid，
		 * 如果没有设置 requestedSessionIdValid，那么就从 request 中解析再赋值 requestedSessionIdValid，
		 * 如果设置了 requestedSessionIdValid，那么就直接取~~~
		 */
		@Override
		public boolean isRequestedSessionIdValid() {

			// 如果还没有设置这个标记，那么就需要立即从请求解析 Session ID -> Session
			if (this.requestedSessionIdValid == null) {
				S requestedSession = getRequestedSession();
				if (requestedSession != null) {
					requestedSession.setLastAccessedTime(Instant.now());
				}
				return isRequestedSessionIdValid(requestedSession);
			}
			return this.requestedSessionIdValid;
		}

		/**
		 * 判断 session 是否有效
		 */
		private boolean isRequestedSessionIdValid(S session) {
			if (this.requestedSessionIdValid == null) {
				this.requestedSessionIdValid = session != null;
			}
			return this.requestedSessionIdValid;
		}

		private boolean isInvalidateClientSession() {
			return getCurrentSession() == null && this.requestedSessionInvalidated;
		}

		@Override
		public HttpSessionWrapper getSession(boolean create) {
			// 获取本地缓存的 Session
			HttpSessionWrapper currentSession = getCurrentSession();
			if (currentSession != null) {
				return currentSession;
			}

			// 从 request 解析 sessionId 并尝试获取 session
			// 为什么是尝试呢？因为可能获取不到，那么返回 null
			S requestedSession = getRequestedSession();


			if (requestedSession != null) {
				if (getAttribute(INVALID_SESSION_ID_ATTR) == null) {

					// 设置最后一次访问时间
					requestedSession.setLastAccessedTime(Instant.now());

					// 记录 session id 有效
					this.requestedSessionIdValid = true;
					currentSession = new HttpSessionWrapper(requestedSession, getServletContext());
					currentSession.markNotNew();
					setCurrentSession(currentSession);
					return currentSession;
				}
			} else {
				// This is an invalid session id. No need to ask again if
				// request.getSession is invoked for the duration of this request
				if (SESSION_LOGGER.isDebugEnabled()) {
					SESSION_LOGGER.debug(
							"No session found by id: Caching result for getSession(false) for this HttpServletRequest.");
				}

				// 标记一个属性，告诉 session 是无效的
				setAttribute(INVALID_SESSION_ID_ATTR, "true");
			}


			// 如果不需要创建新的 Session，那么直接返回 null
			if (!create) {
				return null;
			}


			// 接下来，为客户端创建新的会话

			// 创建新的会话对象，可能需要告诉客户端，所以可能执行 HttpSessionIdResolver 的方法 setSessionId
			// 但是，如果使用 Cookie，response 却已经提交，那么需要报错
			// 其实如果类型是 HeaderHttpSessionIdResolver，response 提交，也不支持
			if (SessionRepositoryFilter.this.httpSessionIdResolver instanceof CookieHttpSessionIdResolver
					&& this.response.isCommitted()) {
				throw new IllegalStateException("Cannot create a session after the response has been committed");
			}


			if (SESSION_LOGGER.isDebugEnabled()) {
				SESSION_LOGGER.debug(
						"A new session was created. To help you troubleshoot where the session was created we provided a StackTrace (this is not an error). You can prevent this from appearing by disabling DEBUG logging for "
								+ SESSION_LOGGER_NAME,
						new RuntimeException("For debugging purposes only (not an error)"));
			}


			// 使用 Session 仓库创建 Session，然后做一些初始化，其实也就是 touch 一下，设置最后一次访问时间
			S session = SessionRepositoryFilter.this.sessionRepository.createSession();
			session.setLastAccessedTime(Instant.now());

			// 将 Session 包装到 HttpSessionWrapper，得到一个 currentSession，设置到 request attribute 中
			currentSession = new HttpSessionWrapper(session, getServletContext());
			setCurrentSession(currentSession);

			return currentSession;
		}

		@Override
		public HttpSessionWrapper getSession() {
			return getSession(true);
		}

		@Override
		public String getRequestedSessionId() {
			if (this.requestedSessionId == null) {
				getRequestedSession();
			}
			return this.requestedSessionId;
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			RequestDispatcher requestDispatcher = super.getRequestDispatcher(path);
			return new SessionCommittingRequestDispatcher(requestDispatcher);
		}

		private S getRequestedSession() {

			// requestedSessionCached 只有 1 次

			if (!this.requestedSessionCached) {

				// @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

				// 调用重要的 HttpSessionIdResolver 解析 SessionId
				List<String> sessionIds = SessionRepositoryFilter.this.httpSessionIdResolver.resolveSessionIds(this);

				// 可能解析多个
				for (String sessionId : sessionIds) {
					// init: 只是初始化而已
					if (this.requestedSessionId == null) {
						this.requestedSessionId = sessionId;
					}

					// 从仓库找这和会话对象
					S session = SessionRepositoryFilter.this.sessionRepository.findById(sessionId);

					// 如果找到了这个会话对象，就表示这是有效的 Session ID
					if (session != null) {
						// 记录下这个有效的会话对象
						this.requestedSession = session;
						// 如果会话对象是真实的，就覆盖，如果从未覆盖，那么就可能解析到一个无效的会话ID
						// 所以 requestedSessionId 可能有效、可能无效
						this.requestedSessionId = sessionId;
						break;
					}
				}

				// @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

				this.requestedSessionCached = true;
			}
			return this.requestedSession;
		}

		private void clearRequestedSessionCache() {
			// 清空是否缓存过的标记，这使得可以继续获取新的会话
			this.requestedSessionCached = false;
			// 清除会话对象
			this.requestedSession = null;
			// 清除会话ID
			this.requestedSessionId = null;
		}

		/**
		 * Allows creating an HttpSession from a Session instance.
		 *
		 * @author Rob Winch
		 * @since 1.0
		 */
		private final class HttpSessionWrapper extends HttpSessionAdapter<S> {

			HttpSessionWrapper(S session, ServletContext servletContext) {
				super(session, servletContext);
			}

			@Override
			public void invalidate() {
				// 先标记一下我已经调用过 invalidate 方法
				super.invalidate();

				// 然后在 RequestWrapper 里面再标记一下
				SessionRepositoryRequestWrapper.this.requestedSessionInvalidated = true;

				// 删除 Request 存储的属性
				setCurrentSession(null);

				// 清除 3 个标记
				clearRequestedSessionCache();

				// 从会话仓库中删除
				SessionRepositoryFilter.this.sessionRepository.deleteById(getId());
			}

		}

		/**
		 * Ensures session is committed before issuing an include.
		 *
		 * @since 1.3.4
		 */
		private final class SessionCommittingRequestDispatcher implements RequestDispatcher {

			private final RequestDispatcher delegate;

			SessionCommittingRequestDispatcher(RequestDispatcher delegate) {
				this.delegate = delegate;
			}

			@Override
			public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {
				this.delegate.forward(request, response);
			}

			@Override
			public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException {
				SessionRepositoryRequestWrapper.this.commitSession();
				this.delegate.include(request, response);
			}

		}

	}

}
