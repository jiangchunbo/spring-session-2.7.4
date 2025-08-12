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

import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.session.Session;

/**
 * Adapts Spring Session's {@link Session} to an {@link HttpSession}.
 * <p>
 * 将 Spring Session 适配为一个 Http Session。Http Session 是一个规范。
 * <p>
 * 这个类没有使用 public 修饰，说明它只希望被包内的其他类使用，就是作为其父类
 *
 * @param <S> the {@link Session} type
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.1
 */
@SuppressWarnings("deprecation")
class HttpSessionAdapter<S extends Session> implements HttpSession {

	private static final Log logger = LogFactory.getLog(HttpSessionAdapter.class);

	/**
	 * 这个适配层的作用就是把 Spring Session 自己的会话对象存期来，然后使用一种类似代理的方式发挥作用
	 */
	private S session;

	private final ServletContext servletContext;

	/**
	 * 标识是否过期
	 */
	private boolean invalidated;

	private boolean old;

	HttpSessionAdapter(S session, ServletContext servletContext) {
		if (session == null) {
			throw new IllegalArgumentException("session cannot be null");
		}
		if (servletContext == null) {
			throw new IllegalArgumentException("servletContext cannot be null");
		}
		this.session = session;
		this.servletContext = servletContext;
	}

	S getSession() {
		return this.session;
	}

	@Override
	public long getCreationTime() {
		// 如果过期了，就抛出异常
		checkState();
		// 从 Session 中获取创建时间
		return this.session.getCreationTime().toEpochMilli();
	}

	@Override
	public String getId() {
		// 从 Session 获取 ID
		return this.session.getId();
	}

	@Override
	public long getLastAccessedTime() {
		// 如果过期了，就抛出异常
		checkState();
		// 从 Session 中获取最后一次访问时间
		return this.session.getLastAccessedTime().toEpochMilli();
	}

	@Override
	public ServletContext getServletContext() {
		// 返回构造器传入的 ServletContext
		return this.servletContext;
	}

	@Override
	public void setMaxInactiveInterval(int interval) {
		// 设置最大不活跃间隔
		// 或者称之为会话超时时间
		// 单位：秒
		// 这个值能设置说明它是一个可变量！！！
		this.session.setMaxInactiveInterval(Duration.ofSeconds(interval));
	}

	@Override
	public int getMaxInactiveInterval() {
		// 获取最大不活跃间隔
		return (int) this.session.getMaxInactiveInterval().getSeconds();
	}

	@Override
	public HttpSessionContext getSessionContext() {
		// 获取 HttpSessionContext
		// 是一个 NOOP -> no operation 的操作
		return NOOP_SESSION_CONTEXT;
	}

	@Override
	public Object getAttribute(String name) {
		// 获取属性

		// 检查状态是否过期
		checkState();
		// 从 session 中获取属性
		return this.session.getAttribute(name);
	}

	@Override
	public Object getValue(String name) {
		// 过时了，使用 getAttribute 替代
		return getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		// 获取所有属性名

		// 检查状态
		checkState();

		// 直接粗暴获取所有属性名，并包装成一个 Enumeration
		return Collections.enumeration(this.session.getAttributeNames());
	}

	@Override
	public String[] getValueNames() {
		// 获取所有值的名称
		// 类似 getValue 都是过时的

		// 检查状态
		checkState();
		// 粗暴获取所有属性名，是个 Set，由此可见，属性名是不能重复的
		Set<String> attrs = this.session.getAttributeNames();
		// 转换为数组
		return attrs.toArray(new String[0]);
	}

	@Override
	public void setAttribute(String name, Object value) {
		// 检查状态
		checkState();

		// 获取旧的值，待会可能要调用它的方法 -> valueUnbound
		Object oldValue = this.session.getAttribute(name);

		// 设置新的值
		this.session.setAttribute(name, value);

		// 如果值不相等
		if (value != oldValue) {
			// 如果实现了 HttpSessionBindingListener，说明这个[旧值]可以监听自己被取消绑定的事情
			if (oldValue instanceof HttpSessionBindingListener) {
				try {
					((HttpSessionBindingListener) oldValue)
							.valueUnbound(new HttpSessionBindingEvent(this, name, oldValue));
				} catch (Throwable th) {
					logger.error("Error invoking session binding event listener", th);
				}
			}

			// 如果实现了 HttpSessionBindingListener，说明这个[新值]可以监听自己被绑定到某个会话的事情
			if (value instanceof HttpSessionBindingListener) {
				try {
					((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(this, name, value));
				} catch (Throwable th) {
					logger.error("Error invoking session binding event listener", th);
				}
			}
		}
	}

	@Override
	public void putValue(String name, Object value) {
		setAttribute(name, value);
	}

	@Override
	public void removeAttribute(String name) {
		checkState();
		Object oldValue = this.session.getAttribute(name);
		this.session.removeAttribute(name);
		if (oldValue instanceof HttpSessionBindingListener) {
			try {
				((HttpSessionBindingListener) oldValue).valueUnbound(new HttpSessionBindingEvent(this, name, oldValue));
			} catch (Throwable th) {
				logger.error("Error invoking session binding event listener", th);
			}
		}
	}

	@Override
	public void removeValue(String name) {
		removeAttribute(name);
	}

	@Override
	public void invalidate() {
		checkState();

		// 由于这个标记的作用，只能调用一次 invalidate，否则上面 checkState 都会报错
		this.invalidated = true;
	}

	@Override
	public boolean isNew() {
		checkState();
		return !this.old;
	}

	void markNotNew() {
		this.old = true;
	}

	private void checkState() {
		if (this.invalidated) {
			throw new IllegalStateException("The HttpSession has already be invalidated.");
		}
	}

	private static final HttpSessionContext NOOP_SESSION_CONTEXT = new HttpSessionContext() {

		@Override
		public HttpSession getSession(String sessionId) {
			return null;
		}

		@Override
		public Enumeration<String> getIds() {
			return EMPTY_ENUMERATION;
		}

	};

	private static final Enumeration<String> EMPTY_ENUMERATION = new Enumeration<String>() {

		@Override
		public boolean hasMoreElements() {
			return false;
		}

		@Override
		public String nextElement() {
			throw new NoSuchElementException("a");
		}

	};

}
