package com.netflix.spinnaker.orca.locks

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class RedisLockManagerSpec extends Specification {

  @Shared
  EmbeddedRedis redisServer

  @Shared
  Pool<Jedis> pool

  @Subject
  RedisLockManager redisLockManager

  void setupSpec() {
    redisServer = EmbeddedRedis.embed()
    pool = redisServer.pool
  }

  void cleanupSpec() {
    pool.close()
    redisServer.destroy()
  }

  void setup() {
    pool.resource.withCloseable { Jedis jedis -> jedis.flushAll() }
    redisLockManager = new RedisLockManager(pool, new LockingConfigurationProperties(learningMode: false))
  }

  def "should acquire a lock if none exists"() {
    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 1)

    then:
    noExceptionThrown()
  }

  def "should acquire a lock if already exists but same lockValue supplied"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)

    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'buzz', 300)

    then:
    noExceptionThrown()
  }

  def "acquiring a lock should set TTL"() {
    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)

    then:
    pool.resource.withCloseable { Jedis jedis ->
      jedis.ttl(RedisLockManager.getLockKey('foo')) > 290
    }
  }

  def "reacquiring a lock should reset TTL"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 100)

    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)

    then:
    pool.resource.withCloseable { Jedis jedis ->
      jedis.ttl(RedisLockManager.getLockKey('foo')) > 290
    }
  }

  def "extending a lock should reset TTL"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 100)

    when:
    redisLockManager.extendLock('foo', 'fooapp', 'pipeline', 'bar', 300)

    then:
    pool.resource.withCloseable { Jedis jedis ->
      jedis.ttl(RedisLockManager.getLockKey('foo')) > 290
    }
  }

  def "should fail to extend a lock if held by a different lockValue"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 100)

    when:
    redisLockManager.extendLock('foo', 'fooapp', 'pipeline', 'bazinga', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValue == 'bar'
  }

  def "should fail to extend a lock if held by a different lockValueApplication"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 100)

    when:
    redisLockManager.extendLock('foo', 'fooapp2', 'pipeline', 'bar', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValueApplication == 'fooapp'
    ex.currentLockValue == 'bar'
  }

  def "should fail to extend a lock if held by a different lockValueType"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 100)

    when:
    redisLockManager.extendLock('foo', 'fooapp', 'orchestration', 'bar', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValueType == 'pipeline'
    ex.currentLockValue == 'bar'
  }

  def "should fail to extend an unknown lock"() {
    when:
    redisLockManager.extendLock('foo', 'fooapp', 'pipeline', 'bazinga', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValue == null

  }

  def "should fail to acquire a lock if already held with a different lockValue"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)

    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bazinga', 'baz', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValue == 'bar'
  }

  def "should be able to release non-existant lock"() {
    when:
    redisLockManager.releaseLock('foo', 'fooapp', 'pipeline', 'bar', 'baz')

    then:
    noExceptionThrown()
  }

  def "releasing only lock holder should free the lock"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)

    when:
    redisLockManager.releaseLock('foo', 'fooapp', 'pipeline', 'bar', 'baz')

    then:
    pool.resource.withCloseable { Jedis jedis ->
      !jedis.exists(RedisLockManager.getLockKey('foo'))
    }
  }

  def "releasing one of many lockHolders doesn't free the lock"() {
    given:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'baz', 300)
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bar', 'buzz', 300)
    redisLockManager.releaseLock('foo', 'fooapp', 'pipeline', 'bar', 'buzz')

    when:
    redisLockManager.acquireLock('foo', 'fooapp', 'pipeline', 'bazinga', 'bizz', 300)

    then:
    def ex = thrown(LockFailureException)
    ex.currentLockValue == 'bar'
  }

}
