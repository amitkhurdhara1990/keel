/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import redis.clients.jedis.JedisPool
import java.time.Clock

@TestInstance(Lifecycle.PER_CLASS)
object RedisTraceRepositoryTest {

  val embeddedRedis = EmbeddedRedis.embed()
  val jedisPool = embeddedRedis.pool as JedisPool
  val objectMapper = ObjectMapper().apply {
    registerSubtypes(TestIntent::class.java)
    registerModule(KotlinModule())
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }
  val clock = Clock.systemDefaultZone()
  val traceRepository = RedisTraceRepository(JedisClientDelegate(jedisPool), null, objectMapper, clock)

  @BeforeEach
  fun setup() {
    jedisPool.resource.use {
      it.flushDB()
    }
  }

  @AfterAll
  fun cleanup() {
    embeddedRedis.destroy()
  }

  @Test
  fun `listing traces for an intent returns ordered traces`() {
    traceRepository.record(Trace(emptyMap(), TestIntent(GenericTestIntentSpec("1", mapOf("placement" to 1)), mutableMapOf(), mutableListOf()), null))
    traceRepository.record(Trace(emptyMap(), TestIntent(GenericTestIntentSpec("1", mapOf("placement" to 2)), mutableMapOf(), mutableListOf()), null))
    traceRepository.record(Trace(emptyMap(), TestIntent(GenericTestIntentSpec("2", mapOf("placement" to 3)), mutableMapOf(), mutableListOf()), null))

    traceRepository.getForIntent("test:1").let { result ->
      result.size shouldMatch equalTo(2)
      result
        .map { it.intent.spec as GenericTestIntentSpec }
        .map { it.data["placement"] } == listOf(1, 2)
    }
  }
}
