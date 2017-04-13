package com.netflix.spinnaker.orca.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.libdiffs.ComparableLooseVersion;
import com.netflix.spinnaker.orca.libdiffs.DefaultComparableLooseVersion;
import com.netflix.spinnaker.orca.listeners.ExecutionCleanupListener;
import com.netflix.spinnaker.orca.notifications.scheduling.SuspendedPipelinesNotificationHandler;
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker;
import com.netflix.spinnaker.orca.pipeline.PipelineStarterListener;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack;
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Configuration
@ComponentScan({
  "com.netflix.spinnaker.orca.pipeline",
  "com.netflix.spinnaker.orca.webhook",
  "com.netflix.spinnaker.orca.notifications.scheduling",
  "com.netflix.spinnaker.orca.restart",
  "com.netflix.spinnaker.orca.deprecation"
})
@EnableConfigurationProperties
public class OrcaConfiguration {
  @Bean public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean public Duration minInactivity() {
    return Duration.of(3, MINUTES);
  }

  @Bean(destroyMethod = "") public Scheduler scheduler() {
    return Schedulers.io();
  }

  @Bean @Scope(SCOPE_PROTOTYPE) public ObjectMapper mapper() {
    return OrcaObjectMapper.newInstance();
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStack")
  public PipelineStack pipelineStack() {
    return new InMemoryPipelineStack();
  }

  @Bean @Scope(SCOPE_PROTOTYPE)
  public SuspendedPipelinesNotificationHandler suspendedPipelinesNotificationHandler(Map<String, Object> input) {
    return new SuspendedPipelinesNotificationHandler(input);
  }

  @Bean @Order(Ordered.LOWEST_PRECEDENCE)
  public DefaultExceptionHandler defaultExceptionHandler() {
    return new DefaultExceptionHandler();
  }

  @Bean public ExecutionCleanupListener executionCleanupListener() {
    return new ExecutionCleanupListener();
  }

  @Bean
  public PipelineStarterListener pipelineStarterListener(ExecutionRepository executionRepository, PipelineStartTracker startTracker, ApplicationContext applicationContext) {
    return new PipelineStarterListener(executionRepository, startTracker, applicationContext);
  }

  @Bean
  @ConditionalOnProperty(value = "jarDiffs.enabled", matchIfMissing = false)
  public ComparableLooseVersion comparableLooseVersion() {
    return new DefaultComparableLooseVersion();
  }

  @Bean
  public StageNavigator stageNavigator(ApplicationContext applicationContext) {
    return new StageNavigator(applicationContext);
  }

  @Bean
  @ConfigurationProperties("expressions")
  public ContextFunctionConfiguration contextFunctionConfiguration() {
    return new ContextFunctionConfiguration();
  }

  @Bean
  public ContextParameterProcessor contextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    return new ContextParameterProcessor(contextFunctionConfiguration);
  }

  // TODO: this is a weird place to have this, feels like it should be a bean configurer or something
  public static ThreadPoolTaskExecutor applyThreadPoolMetrics(Registry registry,
                                                              ThreadPoolTaskExecutor executor,
                                                              String threadPoolName) {
    BiConsumer<String, Function<ThreadPoolExecutor, Integer>> createGuage =
      (name, valueCallback) -> {
        Id id = registry
          .createId(format("threadpool.%s", name))
          .withTag("id", threadPoolName);

        registry.gauge(id, executor, ref -> valueCallback.apply(ref.getThreadPoolExecutor()));
      };

    createGuage.accept("activeCount", ThreadPoolExecutor::getActiveCount);
    createGuage.accept("maximumPoolSize", ThreadPoolExecutor::getMaximumPoolSize);
    createGuage.accept("corePoolSize", ThreadPoolExecutor::getCorePoolSize);
    createGuage.accept("poolSize", ThreadPoolExecutor::getPoolSize);
    createGuage.accept("blockingQueueSize", e -> e.getQueue().size());

    return executor;
  }
}
