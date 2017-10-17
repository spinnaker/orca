package com.netflix.spinnaker.orca.locks;

import static net.logstash.logback.argument.StructuredArguments.kv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Arrays.asList;

@Component
public class RedisLockManager implements LockManager {
  private static final int LOCK_VALUE_APPLICATION_IDX = 0;
  private static final int LOCK_VALUE_TYPE_IDX = 1;
  private static final int LOCK_VALUE_IDX = 2;

  private static final int JEDIS_POOL_MAX_ATTEMPTS = 3;
  private static final int JEDIS_POOL_INITIAL_BACKOFF_MILLIS = 10;
  private static final int JEDIS_POOL_BACKOFF_INCREMENT_MILLIS = 10;

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Pool<Jedis> jedisPool;
  private final boolean learningMode;

  @Autowired
  public RedisLockManager(Pool<Jedis> jedisPool, LockingConfigurationProperties lockingConfigurationProperties) {
    this.jedisPool = jedisPool;
    this.learningMode = lockingConfigurationProperties.isLearningMode();
  }

  @Override
  public void acquireLock( String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder, int ttlSeconds ) throws LockFailureException {
    withLearningMode(LockOperation.acquire(lockName, lockValueApplication, lockValueType, lockValue, lockHolder, ttlSeconds), (op) -> {
      final List<String> result = withJedis(jedis -> (List<String>) jedis.eval(ACQUIRE_LOCK, op.key(), op.acquireArgs()));
      checkResult(op, result);
    });
  }

  @Override
  public void extendLock(String lockName, String lockValueApplication, String lockValueType, String lockValue, int ttlSeconds) throws LockFailureException {
    withLearningMode(LockOperation.extend(lockName, lockValueApplication, lockValueType, lockValue, ttlSeconds), (op) -> {
      final List<String> result = withJedis(jedis ->
        (List<String>) jedis.eval(EXTEND_LOCK, op.key(), op.extendArgs()));
      checkResult(op, result);
    });
  }

  @Override
  public void releaseLock(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder) {
    withLearningMode(LockOperation.release(lockName, lockValueApplication, lockValueType, lockValue, lockHolder), (op) ->
      withJedis(jedis -> jedis.eval(RELEASE_LOCK, op.key(), op.releaseArgs())));
  }

  private static class LockOperation {
    static LockOperation acquire(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder, int ttlSeconds) {
      return new LockOperation("acquireLock", lockName, lockValueApplication, lockValueType, lockValue, lockHolder, ttlSeconds);
    }

    static LockOperation extend(String lockName, String lockValueApplication, String lockValueType, String lockValue, int ttlSeconds) {
      return new LockOperation("extendLock", lockName, lockValueApplication, lockValueType, lockValue, null, ttlSeconds);
    }

    static LockOperation release(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder) {
      return new LockOperation("releaseLock", lockName, lockValueApplication, lockValueType, lockValue, lockHolder, -1);
    }

    final String operationName;
    final String lockName;
    final String lockValueApplication;
    final String lockValueType;
    final String lockValue;
    final String lockHolder;
    final int ttlSeconds;

    private LockOperation(String operationName, String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder, int ttlSeconds) {
      this.operationName = Objects.requireNonNull(operationName);
      this.lockName = Objects.requireNonNull(lockName);
      this.lockValueApplication = Objects.requireNonNull(lockValueApplication);
      this.lockValueType = Objects.requireNonNull(lockValueType);
      this.lockValue = Objects.requireNonNull(lockValue);
      this.lockHolder = lockHolder;
      this.ttlSeconds = ttlSeconds;
    }

    List<String> key() {
      return asList(getLockKey(lockName));
    }

    List<String> acquireArgs() {
      return asList(lockValueApplication, lockValueType, lockValue, lockHolder, Integer.toString(ttlSeconds));
    }

    List<String> extendArgs() {
      return asList(lockValueApplication, lockValueType, lockValue, Integer.toString(ttlSeconds));
    }

    List<String> releaseArgs() {
      return asList(lockValueApplication, lockValueType, lockValue, lockHolder);
    }
  }

  private void checkResult(LockOperation op, List<String> result) {
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("Unexpected result from redis: " + result);
    }
    if (!(op.lockValueApplication.equals(result.get(LOCK_VALUE_APPLICATION_IDX)) &&
      op.lockValueType.equals(result.get(LOCK_VALUE_TYPE_IDX)) &&
      op.lockValue.equals(result.get(LOCK_VALUE_IDX)))) {
      throw new LockFailureException(op.lockName, result.get(LOCK_VALUE_APPLICATION_IDX), result.get(LOCK_VALUE_TYPE_IDX), result.get(LOCK_VALUE_IDX));
    }
  }

  private void withLearningMode(LockOperation lockOperation, Consumer<LockOperation> lockManagementOperation) throws LockFailureException {
    try {
      lockManagementOperation.accept(lockOperation);
    } catch (Throwable t) {
      if (t instanceof LockFailureException) {
        LockFailureException lfe = (LockFailureException) t;
        log.info("LockFailureException during {} for lock {} currently held by {} {} {} requested by {} {} {} {}",
          kv("operationName", lockOperation.operationName),
          kv("lockName", lockOperation.lockName),
          kv("currentLockValueApplication", lfe.getCurrentLockValueApplication()),
          kv("currentLockValueType", lfe.getCurrentLockValueType()),
          kv("currentLockValue", lfe.getCurrentLockValue()),
          kv("requestLockValueApplication", lockOperation.lockValueApplication),
          kv("requestLockValueType", lockOperation.lockValueType),
          kv("requestLockValue", lockOperation.lockValue),
          kv("requestLockHolder", Optional.ofNullable(lockOperation.lockHolder).orElse("UNSPECIFIED")),
          lfe);
        if (learningMode) {
          return;
        }
        throw lfe;
      } else {
        log.info("Exception during {} for lock {} requested by {} {} {} {}",
          kv("operationName", lockOperation.operationName),
          kv("operationName", lockOperation.lockName),
          kv("requestLockValueApplication", lockOperation.lockValueApplication),
          kv("requestLockValueType", lockOperation.lockValueType),
          kv("requestLockValue", lockOperation.lockValue),
          kv("requestLockHolder", Optional.ofNullable(lockOperation.lockHolder).orElse("UNSPECIFIED")),
          t);
        if (learningMode) {
          return;
        }

        if (t instanceof RuntimeException) {
          throw (RuntimeException) t;
        }
        throw new RuntimeException("Exception in RedisLockManager", t);
      }
    }

  }

  private <T> T withJedis(Function<Jedis, T> jedisFunction) {
    Jedis jedis = null;
    Throwable lastException = null;
    for (int i = 0; i < JEDIS_POOL_MAX_ATTEMPTS && jedis == null; i++) {
      try {
        jedis = jedisPool.getResource();
      } catch (Throwable t) {
        lastException = t;
        try {
          Thread.sleep(JEDIS_POOL_INITIAL_BACKOFF_MILLIS + (i * JEDIS_POOL_BACKOFF_INCREMENT_MILLIS));
        } catch (InterruptedException inter) {
          break;
        }
      }
    }
    if (jedis == null) {
      if (lastException == null) {
        throw new NoSuchElementException("Failed to obtain jedis connection from pool, no exception provided");
      } else if (lastException instanceof RuntimeException) {
        throw (RuntimeException) lastException;
      } else {
        throw new RuntimeException("Failed to obtain jedis connection from pool", lastException);
      }
    }
    try {
      return jedisFunction.apply(jedis);
    } finally {
      try {
        jedis.close();
      } catch (Exception ignored) {

      }
    }
  }

  static String getLockKey(String lockName) {
    return "namedlock:" + lockName;
  }

  private static final String ACQUIRE_LOCK = "" +
    "local lockKey, lockValueApplication, lockValueType, lockValue, holderHashKey, ttlSeconds = KEYS[1], ARGV[1], ARGV[2], ARGV[3], 'lockHolder.' .. ARGV[4], tonumber(ARGV[5]);" +
    "if redis.call('exists', lockKey) == 1 then" +
    "  if not (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and " +
    "          redis.call('hget', lockKey, 'lockValueType') == lockValueType and" +
    "          redis.call('hget', lockKey, 'lockValue') == lockValue) then" +
    "    return redis.call('hmget', lockKey, 'lockValueApplication', 'lockValueType', 'lockValue');" +
    "  end;" +
    "end;" +
    "redis.call('hmset', lockKey, 'lockValueApplication', lockValueApplication, 'lockValueType', lockValueType, 'lockValue', lockValue, holderHashKey, 'true');" +
    "redis.call('expire', lockKey, ttlSeconds);" +
    "return {lockValueApplication, lockValueType, lockValue};";

  private static final String EXTEND_LOCK = "" +
    "local lockKey, lockValueApplication, lockValueType, lockValue, ttlSeconds = KEYS[1], ARGV[1], ARGV[2], ARGV[3], tonumber(ARGV[4]);" +
    "if not (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and " +
    "        redis.call('hget', lockKey, 'lockValueType') == lockValueType and" +
    "        redis.call('hget', lockKey, 'lockValue') == lockValue) then" +
    "  return redis.call('hmget', lockKey, 'lockValueApplication', 'lockValueType', 'lockValue');" +
    "end;" +
    "redis.call('expire', lockKey, ttlSeconds);" +
    "return {lockValueApplication, lockValueType, lockValue};";

  private static final String RELEASE_LOCK = "" +
    "local lockKey, lockValueApplication, lockValueType, lockValue, holderHashKey = KEYS[1], ARGV[1], ARGV[2], ARGV[3], 'lockHolder.' .. ARGV[4];" +
    "if (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and " +
    "    redis.call('hget', lockKey, 'lockValueType') == lockValueType and" +
    "    redis.call('hget', lockKey, 'lockValue') == lockValue) then" +
    "  redis.call('hdel', lockKey, holderHashKey);" +
    "  if (redis.call('hlen', lockKey) == 3) then" +
    "    redis.call('del', lockKey);" +
    "  end;" +
    "end;" +
    "return 1;";
}
