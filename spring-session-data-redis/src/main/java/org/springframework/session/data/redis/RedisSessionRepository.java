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

package org.springframework.session.data.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

/**
 * A {@link SessionRepository} implementation that uses Spring Data's
 * {@link RedisOperations} to store sessions is Redis.
 * <p>
 * This implementation does not support publishing of session events.
 *
 * @author Vedran Pavic
 * @since 2.2.0
 */
public class RedisSessionRepository implements SessionRepository<RedisSessionRepository.RedisSession> {

	/**
	 * The default namespace for each key and channel in Redis used by Spring Session.
	 */
	public static final String DEFAULT_KEY_NAMESPACE = "spring:session";

	/**
	 * 操作 Redis 的工具
	 */
	private final RedisOperations<String, Object> sessionRedisOperations;

	/**
	 * 默认最大 timeout，或者说是最大不活跃时间间隔
	 */
	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	/**
	 * key 的名称空间。再加上一个冒号
	 */
	private String keyNamespace = DEFAULT_KEY_NAMESPACE + ":";

	/**
	 * 刷数据的策略。只有两种情况 ON_SAVE 或者 IMMEDIATE，所以源码基本上不会主动判断是否是 ON_SAVE，只会判断是否 IMMEDIATE，也就是立即刷
	 */
	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

	/**
	 * Create a new {@link RedisSessionRepository} instance.
	 * @param sessionRedisOperations the {@link RedisOperations} to use for managing
	 * sessions
	 */
	public RedisSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {
		Assert.notNull(sessionRedisOperations, "sessionRedisOperations mut not be null");
		this.sessionRedisOperations = sessionRedisOperations;
	}

	/**
	 * Set the default maxInactiveInterval.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the key namespace.
	 * @param keyNamespace the key namespace
	 * @deprecated since 2.4.0 in favor of {@link #setRedisKeyNamespace(String)}
	 */
	@Deprecated
	public void setKeyNamespace(String keyNamespace) {
		Assert.hasText(keyNamespace, "keyNamespace must not be empty");
		this.keyNamespace = keyNamespace;
	}

	/**
	 * Set the Redis key namespace.
	 * @param namespace the Redis key namespace
	 */
	public void setRedisKeyNamespace(String namespace) {
		Assert.hasText(namespace, "namespace must not be empty");
		this.keyNamespace = namespace.trim() + ":";
	}

	/**
	 * Set the flush mode.
	 * @param flushMode the flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode must not be null");
		this.flushMode = flushMode;
	}

	/**
	 * Set the save mode.
	 * @param saveMode the save mode
	 */
	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

	@Override
	public RedisSession createSession() {
		// RedisSession 内部需要依赖 MapSession
		MapSession cached = new MapSession();
		// 这里为什么要设置 MaxInactiveInterval
		// 其实你可以参考 MapSessionRepository，这个仓库创建 Session 也设置了 MaxInactiveInterval
		cached.setMaxInactiveInterval(this.defaultMaxInactiveInterval);


		// 将 MapSession 包装成 RedisSession
		RedisSession session = new RedisSession(cached, true);
		// 如果你的 flushMode 是 IMMEDIATE，就会帮你刷入
		session.flushIfRequired();
		return session;
	}

	@Override
	public void save(RedisSession session) {
		if (!session.isNew) {
			String key = getSessionKey(session.hasChangedSessionId() ? session.originalSessionId : session.getId());
			Boolean sessionExists = this.sessionRedisOperations.hasKey(key);
			if (sessionExists == null || !sessionExists) {
				throw new IllegalStateException("Session was invalidated");
			}
		}
		session.save();
	}

	@Override
	public RedisSession findById(String sessionId) {
		String key = getSessionKey(sessionId);
		Map<String, Object> entries = this.sessionRedisOperations.<String, Object>opsForHash().entries(key);
		if (entries.isEmpty()) {
			return null;
		}
		MapSession session = new RedisSessionMapper(sessionId).apply(entries);
		if (session.isExpired()) {
			deleteById(sessionId);
			return null;
		}
		return new RedisSession(session, false);
	}

	@Override
	public void deleteById(String sessionId) {
		String key = getSessionKey(sessionId);
		this.sessionRedisOperations.delete(key);
	}

	/**
	 * Returns the {@link RedisOperations} used for sessions.
	 * @return the {@link RedisOperations} used for sessions
	 */
	public RedisOperations<String, Object> getSessionRedisOperations() {
		return this.sessionRedisOperations;
	}

	private String getSessionKey(String sessionId) {
		return this.keyNamespace + "sessions:" + sessionId;
	}

	private static String getAttributeKey(String attributeName) {
		return RedisSessionMapper.ATTRIBUTE_PREFIX + attributeName;
	}

	/**
	 * An internal {@link Session} implementation used by this {@link SessionRepository}.
	 */
	final class RedisSession implements Session {

		private final MapSession cached;

		private final Map<String, Object> delta = new HashMap<>();

		private boolean isNew;

		/**
		 * 初始 Session ID。记录这个有什么用，可以与 cached 里面的 id 比较，判断是否 ID 进行过更改(change)
		 */
		private String originalSessionId;

		/**
		 * 构造器
		 * @param cached 需要传入一个 MapSession
		 * @param isNew 表示是否是新的？
		 */
		RedisSession(MapSession cached, boolean isNew) {
			this.cached = cached;
			this.isNew = isNew;

			// 原始 sessionId
			this.originalSessionId = cached.getId();

			// 如果这是新建的 Session，那么就要设置一些初始值( create time 、max inactive interval key、 last access )
			if (this.isNew) {
				this.delta.put(RedisSessionMapper.CREATION_TIME_KEY, cached.getCreationTime().toEpochMilli());
				this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY,
						(int) cached.getMaxInactiveInterval().getSeconds());
				this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, cached.getLastAccessedTime().toEpochMilli());
			}
			if (this.isNew || (RedisSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
				getAttributeNames().forEach((attributeName) -> this.delta.put(getAttributeKey(attributeName),
						cached.getAttribute(attributeName)));
			}
		}

		@Override
		public String getId() {
			return this.cached.getId();
		}

		/**
		 * 修改 Session ID
		 */
		@Override
		public String changeSessionId() {
			// 修改的是 cached 里面的 Session ID
			// originalSessionId 不会变化
			return this.cached.changeSessionId();
		}

		@Override
		public <T> T getAttribute(String attributeName) {
			T attributeValue = this.cached.getAttribute(attributeName);
			if (attributeValue != null && RedisSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.put(getAttributeKey(attributeName), attributeValue);
			}
			return attributeValue;
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.cached.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
			this.cached.setAttribute(attributeName, attributeValue);
			this.delta.put(getAttributeKey(attributeName), attributeValue);
			flushIfRequired();
		}

		@Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		@Override
		public Instant getCreationTime() {
			return this.cached.getCreationTime();
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.cached.setLastAccessedTime(lastAccessedTime);
			this.delta.put(RedisSessionMapper.LAST_ACCESSED_TIME_KEY, getLastAccessedTime().toEpochMilli());
			flushIfRequired();
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.cached.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			this.delta.put(RedisSessionMapper.MAX_INACTIVE_INTERVAL_KEY, (int) getMaxInactiveInterval().getSeconds());
			flushIfRequired();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.cached.getMaxInactiveInterval();
		}

		@Override
		public boolean isExpired() {
			return this.cached.isExpired();
		}

		/**
		 * 刷数据。仅当 flushMode 是 IMMEDIATE 才会有效，否则调这个方法就是没意义
		 */
		private void flushIfRequired() {
			if (RedisSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
				save();
			}
		}

		private boolean hasChangedSessionId() {
			return !getId().equals(this.originalSessionId);
		}

		/**
		 * 刷数据
		 */
		private void save() {
			saveChangeSessionId();

			// Delta 表示变化量，只保存变化量
			saveDelta();

			// 如果已经 save 过，那么不再是 new
			if (this.isNew) {
				this.isNew = false;
			}
		}

		private void saveChangeSessionId() {
			// 检查 Session Id 是否变更过
			if (hasChangedSessionId()) {
				if (!this.isNew) {
					String originalSessionIdKey = getSessionKey(this.originalSessionId);
					String sessionIdKey = getSessionKey(getId());
					RedisSessionRepository.this.sessionRedisOperations.rename(originalSessionIdKey, sessionIdKey);
				}
				this.originalSessionId = getId();
			}
		}

		private void saveDelta() {
			if (this.delta.isEmpty()) {
				return;
			}

			// 通过 Session ID 获取 Redis 的 Key
			// 命名空间 + session: + ID
			String key = getSessionKey(getId());

			// Redis Hash 操作 putAll
			RedisSessionRepository.this.sessionRedisOperations.opsForHash().putAll(key, new HashMap<>(this.delta));

			// 调用 expireAt 相当于刷新了 TTL
			RedisSessionRepository.this.sessionRedisOperations.expireAt(key,
					Date.from(Instant.ofEpochMilli(getLastAccessedTime().toEpochMilli())
							.plusSeconds(getMaxInactiveInterval().getSeconds())));
			this.delta.clear();
		}

	}

}
