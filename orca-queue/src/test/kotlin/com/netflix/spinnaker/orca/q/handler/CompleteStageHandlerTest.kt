/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.spek.and
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.api.lifecycle.CachingMode.GROUP
import org.jetbrains.spek.subject.SubjectSpek
import org.springframework.context.ApplicationEventPublisher

object CompleteStageHandlerTest : SubjectSpek<CompleteStageHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val publisher: ApplicationEventPublisher = mock()
  val clock = fixedClock()
  val registry = NoopRegistry()
  val contextParameterProcessor: ContextParameterProcessor = mock()

  val stageWithTaskAndAfterStages = object : StageDefinitionBuilder {
    override fun getType() = "stageWithTaskAndAfterStages"

    override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
      graph.add {
        it.type = singleTaskStage.type
        it.name = "After Stage"
        it.context = mapOf("key" to "value")
      }
    }
  }

  val stageThatBlowsUpPlanningAfterStages = object : StageDefinitionBuilder {
    override fun getType() = "stageThatBlowsUpPlanningAfterStages"

    override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
      throw RuntimeException("there is some problem actually")
    }
  }

  subject(GROUP) {
    CompleteStageHandler(
      queue,
      repository,
      publisher,
      clock,
      contextParameterProcessor,
      registry,
      DefaultStageDefinitionBuilderFactory(
        singleTaskStage,
        multiTaskStage,
        stageWithSyntheticBefore,
        stageWithSyntheticAfter,
        stageWithParallelBranches,
        stageWithTaskAndAfterStages,
        stageThatBlowsUpPlanningAfterStages,
        stageWithSyntheticOnFailure
      )
    )
  }

  fun resetMocks() = reset(queue, repository, publisher)

  describe("completing top level stages") {
    setOf(SUCCEEDED, FAILED_CONTINUE).forEach { taskStatus ->
      describe("when a stage's tasks complete with $taskStatus status") {
        and("it is already complete") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = multiTaskStage.type
              multiTaskStage.plan(this)
              tasks[0].status = SUCCEEDED
              tasks[1].status = taskStatus
              tasks[2].status = SUCCEEDED
              status = taskStatus
              endTime = clock.instant().minusSeconds(2).toEpochMilli()
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving $message") {
            subject.handle(message)
          }

          it("ignores the message") {
            verify(repository, never()).storeStage(any())
            verifyZeroInteractions(queue)
            verifyZeroInteractions(publisher)
          }
        }

        and("it is the last stage") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              assertThat(it.status).isEqualTo(taskStatus)
              assertThat(it.endTime).isEqualTo(clock.millis())
            })
          }

          it("completes the execution") {
            verify(queue).push(CompleteExecution(pipeline))
          }

          it("does not emit any commands") {
            verify(queue, never()).push(any<RunTask>())
          }

          it("publishes an event") {
            verify(publisher).publishEvent(check<StageComplete> {
              assertThat(it.executionType).isEqualTo(pipeline.type)
              assertThat(it.executionId).isEqualTo(pipeline.id)
              assertThat(it.stageId).isEqualTo(message.stageId)
              assertThat(it.status).isEqualTo(taskStatus)
            })
          }
        }

        and("there is a single downstream stage") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
            stage {
              refId = "2"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              assertThat(it.status).isEqualTo(taskStatus)
              assertThat(it.endTime).isEqualTo(clock.millis())
            })
          }

          it("runs the next stage") {
            verify(queue).push(StartStage(
              message.executionType,
              message.executionId,
              message.application,
              pipeline.stages.last().id
            ))
          }

          it("does not run any tasks") {
            verify(queue, never()).push(any<RunTask>())
          }
        }

        and("there are multiple downstream stages") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
            stage {
              refId = "2"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
            stage {
              refId = "3"
              requisiteStageRefIds = setOf("1")
              type = singleTaskStage.type
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("runs the next stages") {
            argumentCaptor<StartStage>().apply {
              verify(queue, times(2)).push(capture())
              assertThat(allValues.map { it.stageId }.toSet()).isEqualTo(pipeline.stages[1..2].map { it.id }.toSet())
            }
          }
        }

        and("there are parallel stages still running") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = singleTaskStage.type
              singleTaskStage.plan(this)
              tasks.first().status = taskStatus
              status = RUNNING
            }
            stage {
              refId = "2"
              type = singleTaskStage.type
              status = RUNNING
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          action("the handler receives a message") {
            subject.handle(message)
          }

          it("still signals completion of the execution") {
            verify(queue).push(CompleteExecution(pipeline))
          }
        }

        setOf(CANCELED, TERMINAL, STOPPED).forEach { failureStatus ->
          and("there are parallel stages that failed") {
            val pipeline = pipeline {
              stage {
                refId = "1"
                type = singleTaskStage.type
                singleTaskStage.plan(this)
                tasks.first().status = SUCCEEDED
                status = RUNNING
              }
              stage {
                refId = "2"
                type = singleTaskStage.type
                status = failureStatus
              }
            }
            val message = CompleteStage(pipeline.stageByRef("1"))

            beforeGroup {
              whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving $message") {
              subject.handle(message)
            }

            it("still signals completion of the execution") {
              verify(queue).push(CompleteExecution(pipeline))
            }
          }
        }

        and("there are still synthetic stages to plan") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              name = "wait"
              status = RUNNING
              type = stageWithTaskAndAfterStages.type
              stageWithTaskAndAfterStages.plan(this)
              tasks.first().status = taskStatus
            }
          }

          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            assertThat(pipeline.stages.map { it.type }).isEqualTo(listOf(stageWithTaskAndAfterStages.type))
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("adds a new AFTER_STAGE") {
            assertThat(pipeline.stages.map { it.type }).isEqualTo(listOf("stageWithTaskAndAfterStages", "singleTaskStage"))
          }

          it("starts the new AFTER_STAGE") {
            verify(queue).push(StartStage(message, pipeline.stages[1].id))
          }

          it("does not signal completion of the execution") {
            verify(queue, never()).push(isA<CompleteExecution>())
          }
        }

        and("planning synthetic stages throws an exception") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              name = "wait"
              status = RUNNING
              type = stageThatBlowsUpPlanningAfterStages.type
              stageThatBlowsUpPlanningAfterStages.plan(this)
              tasks.first().status = taskStatus
            }
          }

          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            assertThat(pipeline.stages.map { it.type }).isEqualTo(listOf(stageThatBlowsUpPlanningAfterStages.type))
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("makes the stage TERMINAL") {
            assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
          }

          it("runs cancellation") {
            verify(queue).push(CancelStage(pipeline.stageById(message.stageId)))
          }

          it("signals completion of the execution") {
            verify(queue).push(CompleteExecution(pipeline))
          }
        }
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      describe("when a stage's task fails with $taskStatus status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = multiTaskStage.type
            multiTaskStage.plan(this)
            tasks[0].status = SUCCEEDED
            tasks[1].status = taskStatus
            tasks[2].status = NOT_STARTED
            status = RUNNING
          }
          stage {
            refId = "2"
            requisiteStageRefIds = listOf("1")
            type = singleTaskStage.type
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"))

        beforeGroup {
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            assertThat(it.status).isEqualTo(taskStatus)
            assertThat(it.endTime).isEqualTo(clock.millis())
          })
        }

        it("does not run any downstream stages") {
          verify(queue, never()).push(isA<StartStage>())
        }

        it("fails the execution") {
          verify(queue).push(CompleteExecution(
            message.executionType,
            message.executionId,
            message.application
          ))
        }

        it("runs the stage's cancellation routine") {
          verify(queue).push(CancelStage(message))
        }

        it("publishes an event") {
          verify(publisher).publishEvent(check<StageComplete> {
            assertThat(it.executionType).isEqualTo(pipeline.type)
            assertThat(it.executionId).isEqualTo(pipeline.id)
            assertThat(it.stageId).isEqualTo(message.stageId)
            assertThat(it.status).isEqualTo(taskStatus)
          })
        }
      }
    }

    describe("when none of a stage's tasks ever started") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = multiTaskStage.type
          multiTaskStage.plan(this)
          tasks[0].status = NOT_STARTED
          tasks[1].status = NOT_STARTED
          tasks[2].status = NOT_STARTED
          status = RUNNING
        }
        stage {
          refId = "2"
          requisiteStageRefIds = listOf("1")
          type = singleTaskStage.type
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("updates the stage state") {
        verify(repository).storeStage(check {
          assertThat(it.status).isEqualTo(TERMINAL)
          assertThat(it.endTime).isEqualTo(clock.millis())
        })
      }

      it("does not run any downstream stages") {
        verify(queue, never()).push(isA<StartStage>())
      }

      it("fails the execution") {
        verify(queue).push(CompleteExecution(
          message.executionType,
          message.executionId,
          message.application
        ))
      }

      it("runs the stage's cancellation routine") {
        verify(queue).push(CancelStage(message))
      }

      it("publishes an event") {
        verify(publisher).publishEvent(check<StageComplete> {
          assertThat(it.executionType).isEqualTo(pipeline.type)
          assertThat(it.executionId).isEqualTo(pipeline.id)
          assertThat(it.stageId).isEqualTo(message.stageId)
          assertThat(it.status).isEqualTo(TERMINAL)
        })
      }
    }

    mapOf(STAGE_BEFORE to stageWithSyntheticBefore, STAGE_AFTER to stageWithSyntheticAfter).forEach { syntheticType, stageBuilder ->
      setOf(TERMINAL, CANCELED, STOPPED).forEach { failureStatus ->
        describe("when a $syntheticType synthetic stage completed with $failureStatus") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              status = RUNNING
              type = stageBuilder.type
              if (syntheticType == STAGE_BEFORE) {
                stageBuilder.buildBeforeStages(this)
              } else {
                stageBuilder.buildAfterStages(this)
              }
              stageBuilder.plan(this)
            }
          }
          val message = CompleteStage(pipeline.stageByRef("1"))

          beforeGroup {
            pipeline
              .stages
              .first { it.syntheticStageOwner == syntheticType }
              .status = failureStatus
            whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
          }

          afterGroup(::resetMocks)

          on("receiving the message") {
            subject.handle(message)
          }

          it("updates the stage state") {
            verify(repository).storeStage(check {
              assertThat(it.status).isEqualTo(failureStatus)
              assertThat(it.endTime).isEqualTo(clock.millis())
            })
          }
        }
      }

      describe("when any $syntheticType synthetic stage completed with FAILED_CONTINUE") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            status = RUNNING
            type = stageBuilder.type
            if (syntheticType == STAGE_BEFORE) {
              stageBuilder.buildBeforeStages(this)
            } else {
              stageBuilder.buildAfterStages(this)
            }
            stageBuilder.plan(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1"))

        beforeGroup {
          pipeline
            .stages
            .first { it.syntheticStageOwner == syntheticType }
            .status = FAILED_CONTINUE
          pipeline.stageById(message.stageId).tasks.forEach { it.status = SUCCEEDED }
          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("updates the stage state") {
          verify(repository).storeStage(check {
            assertThat(it.status).isEqualTo(FAILED_CONTINUE)
            assertThat(it.endTime).isEqualTo(clock.millis())
          })
        }
      }
    }
  }

  describe("completing synthetic stages") {
    listOf(SUCCEEDED, FAILED_CONTINUE).forEach { taskStatus ->
      given("a synthetic stage's task completes with $taskStatus") {
        and("it comes before its parent stage") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithSyntheticBefore.type
              stageWithSyntheticBefore.buildBeforeStages(this)
              stageWithSyntheticBefore.buildTasks(this)
            }
          }

          and("there are more before stages") {
            val message = CompleteStage(pipeline.stageByRef("1<1"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StartStage(
                pipeline.stageByRef("1<2")
              ))
            }
          }

          and("it is the last before stage") {
            val message = CompleteStage(pipeline.stageByRef("1<2"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("signals the parent stage to run") {
              verify(queue).push(ContinueParentStage(
                pipeline.stageByRef("1")
              ))
            }
          }
        }

        and("it comes after its parent stage") {
          val pipeline = pipeline {
            stage {
              refId = "1"
              type = stageWithSyntheticAfter.type
              stageWithSyntheticAfter.buildBeforeStages(this)
              stageWithSyntheticAfter.buildTasks(this)
              stageWithSyntheticAfter.buildAfterStages(this)
            }
          }

          and("there are more after stages") {
            val message = CompleteStage(pipeline.stageByRef("1>1"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("runs the next synthetic stage") {
              verify(queue).push(StartStage(
                message.executionType,
                message.executionId,
                message.application,
                pipeline.stages.last().id
              ))
            }
          }

          and("it is the last after stage") {
            val message = CompleteStage(pipeline.stageByRef("1>2"))

            beforeGroup {
              pipeline.stageById(message.stageId).apply {
                status = RUNNING
                singleTaskStage.plan(this)
                tasks.first().status = taskStatus
              }

              whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
            }

            afterGroup(::resetMocks)

            on("receiving the message") {
              subject.handle(message)
            }

            it("signals the completion of the parent stage") {
              verify(queue).push(CompleteStage(
                message.executionType,
                message.executionId,
                message.application,
                pipeline.stages.first().id
              ))
            }
          }
        }
      }
    }

    setOf(TERMINAL, CANCELED).forEach { taskStatus ->
      given("a synthetic stage's task ends with $taskStatus status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            type = stageWithSyntheticBefore.type
            stageWithSyntheticBefore.buildBeforeStages(this)
            stageWithSyntheticBefore.plan(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).apply {
            status = RUNNING
            singleTaskStage.plan(this)
            tasks.first().status = taskStatus
          }

          whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
        }

        on("receiving the message") {
          subject.handle(message)
        }

        afterGroup(::resetMocks)

        it("rolls up to the parent stage") {
          verify(queue).push(message.copy(stageId = pipeline.stageByRef("1").id))
        }

        it("runs the stage's cancel routine") {
          verify(queue).push(CancelStage(message))
        }
      }
    }
  }

  describe("branching stages") {
    listOf(SUCCEEDED, FAILED_CONTINUE).forEach { status ->
      context("when one branch completes with $status") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            name = "parallel"
            type = stageWithParallelBranches.type
            stageWithParallelBranches.buildBeforeStages(this)
            stageWithParallelBranches.buildTasks(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("signals the parent stage to try to run") {
          verify(queue).push(ContinueParentStage(pipeline.stageByRef("1")))
        }
      }

      context("when all branches are complete") {
        val pipeline = pipeline {
          stage {
            refId = "1"
            name = "parallel"
            type = stageWithParallelBranches.type
            stageWithParallelBranches.buildBeforeStages(this)
            stageWithParallelBranches.buildTasks(this)
          }
        }
        val message = CompleteStage(pipeline.stageByRef("1<1"))

        beforeGroup {
          pipeline.stageById(message.stageId).status = RUNNING
          pipeline.stageByRef("1<2").status = SUCCEEDED
          pipeline.stageByRef("1<3").status = SUCCEEDED

          whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
        }

        afterGroup(::resetMocks)

        on("receiving the message") {
          subject.handle(message)
        }

        it("signals the parent stage to try to run") {
          verify(queue).push(ContinueParentStage(pipeline.stageByRef("1")))
        }
      }
    }
  }

  describe("surfacing expression evaluation errors") {
    fun exceptionErrors(stages: List<Stage>): List<*> =
      stages.flatMap {
        ((it.context["exception"] as Map<*, *>)["details"] as Map<*, *>)["errors"] as List<*>
      }

    given("an exception in the stage context") {
      val expressionError = "Expression foo failed for field bar"
      val existingException = "Existing error"
      val pipeline = pipeline {
        stage {
          refId = "1"
          name = "wait"
          context = mapOf(
            "exception" to mapOf("details" to mapOf("errors" to mutableListOf(existingException))),
            PipelineExpressionEvaluator.SUMMARY to mapOf("failedExpression" to listOf(mapOf("description" to expressionError, "level" to "ERROR")))
          )
          status = RUNNING
          type = singleTaskStage.type
          singleTaskStage.plan(this)
          tasks.first().status = SUCCEEDED
        }
      }

      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("should contain evaluation summary as well as the existing error") {
        val errors = exceptionErrors(pipeline.stages)
        assertThat(errors.size).isEqualTo(2)
        expressionError in errors
        existingException in errors
      }
    }

    given("no other exception errors in the stage context") {
      val expressionError = "Expression foo failed for field bar"
      val pipeline = pipeline {
        stage {
          refId = "1"
          name = "wait"
          context = mutableMapOf<String, Any>(
            PipelineExpressionEvaluator.SUMMARY to mapOf("failedExpression" to listOf(mapOf("description" to expressionError, "level" to "ERROR")))
          )
          status = RUNNING
          type = singleTaskStage.type
          singleTaskStage.plan(this)
          tasks.first().status = SUCCEEDED
        }
      }

      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        whenever(repository.retrieve(PIPELINE, pipeline.id)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("should only contain evaluation error message") {
        val errors = exceptionErrors(pipeline.stages)
        assertThat(errors.size).isEqualTo(1)
        expressionError in errors
      }
    }
  }

  given("a stage ends with TERMINAL status") {
    and("it has not run its on failure stages yet") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = stageWithSyntheticOnFailure.type
          stageWithSyntheticOnFailure.buildBeforeStages(this)
          stageWithSyntheticOnFailure.plan(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        pipeline.stageById(message.stageId).apply {
          status = RUNNING
          tasks.first().status = TERMINAL
        }

        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message") {
        subject.handle(message)
      }

      it("plans the first 'OnFailure' stage") {
        val onFailureStage = pipeline.stages.first { it.name == "onFailure1" }
        verify(queue).push(StartStage(onFailureStage))
      }

      it("does not (yet) update the stage status") {
        assertThat(pipeline.stageById(message.stageId).status).isEqualTo(RUNNING)
      }
    }

    and("it has already run its on failure stages") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = stageWithSyntheticOnFailure.type
          stageWithSyntheticOnFailure.buildBeforeStages(this)
          stageWithSyntheticOnFailure.plan(this)
          stageWithSyntheticOnFailure.buildFailureStages(this)
        }
      }
      val message = CompleteStage(pipeline.stageByRef("1"))

      beforeGroup {
        pipeline.stageById(message.stageId).apply {
          status = RUNNING
          tasks.first().status = TERMINAL
        }

        pipeline.stages.filter { it.parentStageId == message.stageId }.forEach {
          it.status = SUCCEEDED
        }

        whenever(repository.retrieve(PIPELINE, message.executionId)) doReturn pipeline
      }

      afterGroup(::resetMocks)

      on("receiving the message again") {
        subject.handle(message)
      }

      it("does not re-plan any 'OnFailure' stages") {
        verify(queue).push(CancelStage(message))
      }

      it("updates the stage status") {
        assertThat(pipeline.stageById(message.stageId).status).isEqualTo(TERMINAL)
      }
    }
  }
})
