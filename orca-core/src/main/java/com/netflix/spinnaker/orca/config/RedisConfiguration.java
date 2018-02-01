/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.config;

import java.lang.reflect.Field;
import java.net.URI;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.util.Pool;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static redis.clients.jedis.Protocol.DEFAULT_DATABASE;

@Configuration
public class RedisConfiguration {

  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setMaxTotal(100);
    config.setMaxIdle(100);
    config.setMinIdle(25);
    return config;
  }

  @Bean(name = "jedisPool")
  @Primary
  public Pool<Jedis> jedisPool(
    @Value("${redis.connection:redis://localhost:6379}") String connection,
    @Value("${redis.timeout:2000}") int timeout,
    GenericObjectPoolConfig redisPoolConfig,
    Registry registry
  ) {
    return createPool(redisPoolConfig, connection, timeout, registry, "jedisPool");
  }

  @Bean(name = "jedisPoolPrevious")
  @ConditionalOnProperty("redis.connectionPrevious")
  @ConditionalOnExpression("${redis.connection} != ${redis.connectionPrevious}")
  JedisPool jedisPoolPrevious(
    @Value("${redis.connectionPrevious:#{null}}") String previousConnection,
    @Value("${redis.timeout:2000}") int timeout,
    Registry registry
  ) {
    return createPool(null, previousConnection, timeout, registry, "jedisPoolPrevious");
  }

  @Bean(name = "redisClientDelegate")
  @Primary
  RedisClientDelegate redisClientDelegate(
    @Qualifier("jedisPool") Pool<Jedis> jedisPool
  ) {
    return new JedisClientDelegate(jedisPool);
  }

  @Bean(name = "previousRedisClientDelegate")
  @ConditionalOnBean(name = "jedisPoolPrevious")
  RedisClientDelegate previousRedisClientDelegate(
    @Qualifier("jedisPoolPrevious") JedisPool jedisPoolPrevious
  ) {
    return new JedisClientDelegate(jedisPoolPrevious);
  }

  @Bean
  HealthIndicator redisHealth(
    @Qualifier("jedisPool") Pool<Jedis> jedisPool
  ) {
    try {
      final Pool<Jedis> src = jedisPool;
      final Field poolAccess = Pool.class.getDeclaredField("internalPool");
      poolAccess.setAccessible(true);
      GenericObjectPool<Jedis> internal = (GenericObjectPool<Jedis>) poolAccess.get(jedisPool);
      return () -> {
        Jedis jedis = null;
        Health.Builder health;
        try {
          jedis = src.getResource();
          if ("PONG".equals(jedis.ping())) {
            health = Health.up();
          } else {
            health = Health.down();
          }
        } catch (Exception ex) {
          health = Health.down(ex);
        } finally {
          if (jedis != null) jedis.close();
        }
        health.withDetail("maxIdle", internal.getMaxIdle());
        health.withDetail("minIdle", internal.getMinIdle());
        health.withDetail("numActive", internal.getNumActive());
        health.withDetail("numIdle", internal.getNumIdle());
        health.withDetail("numWaiters", internal.getNumWaiters());

        return health.build();
      };
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new BeanCreationException("Error creating Redis health indicator", e);
    }
  }

  public static JedisPool createPool(
    GenericObjectPoolConfig redisPoolConfig,
    String connection,
    int timeout,
    Registry registry,
    String poolName
  ) {
    URI redisConnection = URI.create(connection);

    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();

    String redisConnectionPath = isNotEmpty(redisConnection.getPath()) ? redisConnection.getPath() : "/" + DEFAULT_DATABASE;
    int database = Integer.parseInt(redisConnectionPath.split("/", 2)[1]);

    String password = redisConnection.getUserInfo() != null ? redisConnection.getUserInfo().split(":", 2)[1] : null;

    JedisPool jedisPool = new JedisPool(redisPoolConfig != null ? redisPoolConfig : new GenericObjectPoolConfig(), host, port, timeout, password, database, null);
    final Field poolAccess;
    try {
      poolAccess = Pool.class.getDeclaredField("internalPool");
      poolAccess.setAccessible(true);
      GenericObjectPool<Jedis> pool = (GenericObjectPool<Jedis>) poolAccess.get(jedisPool);
      registry.gauge(registry.createId("redis.connectionPool.maxIdle", "poolName", poolName), pool, (GenericObjectPool<Jedis> p) -> Integer.valueOf(p.getMaxIdle()).doubleValue());
      registry.gauge(registry.createId("redis.connectionPool.minIdle", "poolName", poolName), pool, (GenericObjectPool<Jedis> p) -> Integer.valueOf(p.getMinIdle()).doubleValue());
      registry.gauge(registry.createId("redis.connectionPool.numActive", "poolName", poolName), pool, (GenericObjectPool<Jedis> p) -> Integer.valueOf(p.getNumActive()).doubleValue());
      registry.gauge(registry.createId("redis.connectionPool.numIdle", "poolName", poolName), pool, (GenericObjectPool<Jedis> p) -> Integer.valueOf(p.getMaxIdle()).doubleValue());
      registry.gauge(registry.createId("redis.connectionPool.numWaiters", "poolName", poolName), pool, (GenericObjectPool<Jedis> p) -> Integer.valueOf(p.getMaxIdle()).doubleValue());
      return jedisPool;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new BeanCreationException("Error creating Redis pool", e);
    }
  }
}
