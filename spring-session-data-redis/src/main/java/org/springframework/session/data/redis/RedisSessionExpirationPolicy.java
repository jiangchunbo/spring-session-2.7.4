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

package org.springframework.session.data.redis;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;

/**
 * A strategy for expiring {@link RedisSession} instances. This performs two operations:
 * <p>
 * Redis has no guarantees of when an expired session event will be fired. In order to
 * ensure expired session events are processed in a timely fashion the expiration (rounded
 * to the nearest minute) is mapped to all the sessions that expire at that time. Whenever
 * {@link #cleanExpiredSessions()} is invoked, the sessions for the previous minute are
 * then accessed to ensure they are deleted if expired.
 * <p>
 * In some instances the {@link #cleanExpiredSessions()} method may not be not invoked for
 * a specific time. For example, this may happen when a server is restarted. To account
 * for this, the expiration on the Redis session is also set.
 *
 * @author Rob Winch
 * @since 1.0
 */
final class RedisSessionExpirationPolicy {

	private static final Log logger = LogFactory.getLog(RedisSessionExpirationPolicy.class);

	private static final String SESSION_EXPIRES_PREFIX = "expires:";

	private final RedisOperations<Object, Object> redis;

	private final Function<Long, String> lookupExpirationKey;

	private final Function<String, String> lookupSessionKey;

	/**
	 *
	 * @param sessionRedisOperations 简单理解为拿到这个对象就可以操作 redis
	 * @param lookupExpirationKey    寻找过期的 key
	 * @param lookupSessionKey       寻找 session key
	 */
	RedisSessionExpirationPolicy(RedisOperations<Object, Object> sessionRedisOperations,
								 Function<Long, String> lookupExpirationKey, Function<String, String> lookupSessionKey) {
		super();
		this.redis = sessionRedisOperations;
		this.lookupExpirationKey = lookupExpirationKey;
		this.lookupSessionKey = lookupSessionKey;
	}

	void onDelete(Session session) {
		long toExpire = roundUpToNextMinute(expiresInMillis(session));
		String expireKey = getExpirationKey(toExpire);
		String entryToRemove = SESSION_EXPIRES_PREFIX + session.getId();
		this.redis.boundSetOps(expireKey).remove(entryToRemove);
	}

	/**
	 * 删除原先可能存在的分钟集合中的元素
	 *
	 * @param originalExpirationTimeInMilli
	 * @param session
	 */
	void onExpirationUpdated(Long originalExpirationTimeInMilli, Session session) {
		// expires:e3089a07-e30d-49f8-b178-27c8c0ce16f1
		String keyToExpire = SESSION_EXPIRES_PREFIX + session.getId();

		// 计算出 session 在什么时候过期，然后按照分钟向上取整 -> 批量过期
		long toExpire = roundUpToNextMinute(expiresInMillis(session));

		// 如果原先访问过了
		if (originalExpirationTimeInMilli != null) {
			// 获取原先过期的分钟
			long originalRoundedUp = roundUpToNextMinute(originalExpirationTimeInMilli);
			// 如果两次过期的分钟不相等，那么就从之前的集合中删除
			if (toExpire != originalRoundedUp) {
				String expireKey = getExpirationKey(originalRoundedUp);
				this.redis.boundSetOps(expireKey).remove(keyToExpire);
			}
		}

		long sessionExpireInSeconds = session.getMaxInactiveInterval().getSeconds();

		// spring:session:sessions:expires:e3089a07-e30d-49f8-b178-27c8c0ce16f1
		String sessionKey = getSessionKey(keyToExpire);

		// 如果 timeout 设置为 -1，
		if (sessionExpireInSeconds < 0) {
			// 确保键是存在的。append -> 追加空字符串
			this.redis.boundValueOps(sessionKey).append("");
			// 持久化，就是删除 TTL
			this.redis.boundValueOps(sessionKey).persist();
			// 持久化 Session
			this.redis.boundHashOps(getSessionKey(session.getId())).persist();
			return;
		}

		// spring:session:expirations:1758364980000
		// 在这一分钟过期的 session 集合
		String expireKey = getExpirationKey(toExpire);
		BoundSetOperations<Object, Object> expireOperations = this.redis.boundSetOps(expireKey);
		expireOperations.add(keyToExpire);

		// 真正 session 过期时间是 timeout + 5 分钟
		long fiveMinutesAfterExpires = sessionExpireInSeconds + TimeUnit.MINUTES.toSeconds(5);

		// spring:session:expirations:1758364980000 -> 这个集合的时间比 timeout 多 5 分钟
		expireOperations.expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);

		if (sessionExpireInSeconds == 0) {
			// 如果 session 是立即失效，那么就立即删除
			// 业务层可以 setMaxInactiveInterval(0) 使得 session 立即失效
			this.redis.delete(sessionKey);
		} else {
			this.redis.boundValueOps(sessionKey).append("");
			this.redis.boundValueOps(sessionKey).expire(sessionExpireInSeconds, TimeUnit.SECONDS);
		}
		this.redis.boundHashOps(getSessionKey(session.getId())).expire(fiveMinutesAfterExpires, TimeUnit.SECONDS);
	}

	String getExpirationKey(long expires) {
		// this.namespace + "expirations:" + expiration
		// spring:session:expirations: + expiration
		return this.lookupExpirationKey.apply(expires);
	}

	/**
	 * 基于 session 存储的键空间，构造 key
	 * <p>
	 * 这个方法不仅用于构造 session 的 key，也用于构造 session expire 的 key
	 *
	 * @param sessionId 字符串。通常可以认为是 sessionId，有时候传入的是 expire + sessionId
	 * @return redis key
	 */
	String getSessionKey(String sessionId) {
		// this.namespace + "sessions:" + sessionId;
		// "spring:session" + "sessions:" + sessionId
		// 通过这个 key 可以寻找到 session 对象
		return this.lookupSessionKey.apply(sessionId);
	}

	/**
	 * 这个方法没有访问修饰符，所以只能被同一个包访问
	 * <p>
	 * 这个方法被 RedisIndexedSessionRepository 类使用，定时地调用清理 Session
	 */
	void cleanExpiredSessions() {
		long now = System.currentTimeMillis();

		// 获得时间戳，然后向下取整(按分钟)
		long prevMin = roundDownMinute(now);

		if (logger.isDebugEnabled()) {
			logger.debug("Cleaning up sessions expiring at " + new Date(prevMin));
		}

		// 得到一个跟分钟整点相关的 key
		String expirationKey = getExpirationKey(prevMin);

		// 得到所有 member，这些都是需要过期的 -> 意思就是说，这一分钟已经过期的
		Set<Object> sessionsToExpire = this.redis.boundSetOps(expirationKey).members();

		// 清除这个 key，那么所有 member 也消失了
		this.redis.delete(expirationKey);

		// 遍历这些即将过期的 session
		for (Object session : sessionsToExpire) {
			// 获取 session 对象的 key
			String sessionKey = getSessionKey((String) session);

			// 触摸一下，触发 redis 过期
			touch(sessionKey);
		}
	}

	/**
	 * By trying to access the session we only trigger a deletion if it the TTL is
	 * expired. This is done to handle
	 * https://github.com/spring-projects/spring-session/issues/93
	 *
	 * @param key the key
	 */
	private void touch(String key) {
		this.redis.hasKey(key);
	}

	/**
	 * 计算出这个 session 在什么时候会过期。单位：毫秒。
	 *
	 * @param session 会话对象
	 * @return 过期时间戳。单位：毫秒。
	 */
	static long expiresInMillis(Session session) {
		int maxInactiveInSeconds = (int) session.getMaxInactiveInterval().getSeconds();
		long lastAccessedTimeInMillis = session.getLastAccessedTime().toEpochMilli();

		// 上次访问时间 + timeout
		return lastAccessedTimeInMillis + TimeUnit.SECONDS.toMillis(maxInactiveInSeconds);
	}

	static long roundUpToNextMinute(long timeInMs) {

		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(timeInMs);
		date.add(Calendar.MINUTE, 1);
		date.clear(Calendar.SECOND);
		date.clear(Calendar.MILLISECOND);
		return date.getTimeInMillis();
	}

	static long roundDownMinute(long timeInMs) {
		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(timeInMs);
		date.clear(Calendar.SECOND);
		date.clear(Calendar.MILLISECOND);
		return date.getTimeInMillis();
	}

}
