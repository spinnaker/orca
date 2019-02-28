package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Main.class})
@ContextConfiguration(classes = {StartupTestConfiguration.class})
@TestPropertySource(properties = {"spring.config.location=classpath:orca-test.yml"})
public class MainSpec {
  @MockBean
  Front50Service front50Service;

  @MockBean
  ExecutionRepository executionRepository;

  @MockBean
  LockManager lockManager;

  @MockBean
  NotificationClusterLock notificationClusterLock;

  @Test
  public void startupTest() {
  }
}
