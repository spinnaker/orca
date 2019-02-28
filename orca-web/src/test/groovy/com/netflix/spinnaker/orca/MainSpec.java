package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.echo.EchoService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.igor.IgorService;
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
  IgorService igorService;

  @MockBean
  BakeryService bakeryService;

  @MockBean
  EchoService echoService;

  @Test
  public void startupTest() {
  }
}
