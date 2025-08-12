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

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Provides a way to identify a user in an agnostic way. This allows the session to be
 * used by an HttpSession, WebSocket Session, or even non web related sessions.
 * <p>
 * 提供一种与具体技术无关的用户标识方式，使得同一套会话机制既可以用于 HttpSession（传统 HTTP 会话）、WebSocket 会话，
 * 甚至也能应用到非 Web 场景的会话
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 */
public interface Session {

	/**
	 * Gets a unique string that identifies the {@link Session}.
	 * 获取标识这个会话的唯一字符串
	 *
	 * @return a unique string that identifies the {@link Session}
	 */
	String getId();

	/**
	 * Changes the session id. After invoking the {@link #getId()} will return a new
	 * identifier.
	 * <p>
	 * 修改会话 ID。之后，调用 getId 就会得到一个新的标识符
	 *
	 * @return the new session id which {@link #getId()} will now return
	 */
	String changeSessionId();

	/**
	 * Gets the Object associated with the specified name or null if no Object is
	 * associated to that name.
	 * <p>
	 * 获得给定 name 的对昂，如果没有对象关联这个 name 就返回 null
	 *
	 * @param <T>           the return type of the attribute
	 * @param attributeName the name of the attribute to get
	 * @return the Object associated with the specified name or null if no Object is
	 * associated to that name
	 */
	<T> T getAttribute(String attributeName);

	/**
	 * Return the session attribute value or if not present raise an
	 * {@link IllegalArgumentException}.
	 * <p>
	 * 注意到这个 required，返回会话属性值，如果不存在，就抛出异常
	 *
	 * @param name the attribute name
	 * @param <T>  the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getRequiredAttribute(String name) {
		T result = getAttribute(name);
		if (result == null) {
			throw new IllegalArgumentException("Required attribute '" + name + "' is missing.");
		}
		return result;
	}

	/**
	 * Return the session attribute value, or a default, fallback value.
	 * <p>
	 * 返回会话的属性值，或者不存在就返回一个默认值
	 *
	 * @param name         the attribute name
	 * @param defaultValue a default value to return instead
	 * @param <T>          the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getAttributeOrDefault(String name, T defaultValue) {
		T result = getAttribute(name);
		return (result != null) ? result : defaultValue;
	}

	/**
	 * Gets the attribute names that have a value associated with it. Each value can be
	 * passed into {@link org.springframework.session.Session#getAttribute(String)} to
	 * obtain the attribute value.
	 *
	 * @return the attribute names that have a value associated with it.
	 * @see #getAttribute(String)
	 */
	Set<String> getAttributeNames();

	/**
	 * Sets the attribute value for the provided attribute name. If the attributeValue is
	 * null, it has the same result as removing the attribute with
	 * {@link org.springframework.session.Session#removeAttribute(String)} .
	 *
	 * @param attributeName  the attribute name to set
	 * @param attributeValue the value of the attribute to set. If null, the attribute
	 *                       will be removed.
	 */
	void setAttribute(String attributeName, Object attributeValue);

	/**
	 * Removes the attribute with the provided attribute name.
	 *
	 * @param attributeName the name of the attribute to remove
	 */
	void removeAttribute(String attributeName);

	/**
	 * Gets the time when this session was created.
	 *
	 * @return the time when this session was created.
	 */
	Instant getCreationTime();

	/**
	 * Sets the last accessed time.
	 *
	 * @param lastAccessedTime the last accessed time
	 */
	void setLastAccessedTime(Instant lastAccessedTime);

	/**
	 * Gets the last time this {@link Session} was accessed.
	 * <p>
	 * 获取最后一次访问时间
	 *
	 * @return the last time the client sent a request associated with the session
	 */
	Instant getLastAccessedTime();

	/**
	 * Sets the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @param interval the amount of time that the {@link Session} should be kept alive
	 *                 between client requests.
	 */
	void setMaxInactiveInterval(Duration interval);

	/**
	 * Gets the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 *
	 * @return the maximum inactive interval between requests before this session will be
	 * invalidated. A negative time indicates that the session will never timeout.
	 */
	Duration getMaxInactiveInterval();

	/**
	 * Returns true if the session is expired.
	 * <p>
	 * 返回这个会话是否过期
	 *
	 * @return true if the session is expired, else false.
	 */
	boolean isExpired();

}
