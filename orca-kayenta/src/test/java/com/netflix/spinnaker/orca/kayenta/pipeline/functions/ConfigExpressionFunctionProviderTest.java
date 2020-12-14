package com.netflix.spinnaker.orca.kayenta.pipeline.functions;

import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.kayenta.KayentaCanaryConfig;
import com.netflix.spinnaker.orca.kayenta.KayentaService;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ConfigExpressionFunctionProviderTest {

  @Test(expected = SpelHelperFunctionException.class)
  public void missingName() {
    ConfigExpressionFunctionProvider provider =
        new ConfigExpressionFunctionProvider(Mockito.mock(KayentaService.class));
    provider.acaNameToId(null, "myapp");
  }

  @Test(expected = SpelHelperFunctionException.class)
  public void missingApp() {
    ConfigExpressionFunctionProvider provider =
        new ConfigExpressionFunctionProvider(Mockito.mock(KayentaService.class));
    provider.acaNameToId("myname", null);
  }

  @Test
  public void conversionWorks() {
    KayentaService kayentaService = Mockito.mock(KayentaService.class);
    List<KayentaCanaryConfig> canaryConfigs = Lists.newArrayList();
    KayentaCanaryConfig config =
        new KayentaCanaryConfig("myconfig", "myname", 0L, null, Collections.singletonList("myapp"));
    canaryConfigs.add(config);
    when(kayentaService.getAllCanaryConfigs()).thenReturn(canaryConfigs);

    ConfigExpressionFunctionProvider provider =
        new ConfigExpressionFunctionProvider(kayentaService);
    String configId = provider.acaNameToId("myname", "myapp");

    Assert.assertEquals("myconfig", configId);
  }

  @Test(expected = SpelHelperFunctionException.class)
  public void nothingFound() {
    KayentaService kayentaService = Mockito.mock(KayentaService.class);
    List<KayentaCanaryConfig> canaryConfigs = Lists.newArrayList();
    KayentaCanaryConfig config =
        new KayentaCanaryConfig("myconfig", "myname", 0L, null, Collections.singletonList("myapp"));
    canaryConfigs.add(config);
    when(kayentaService.getAllCanaryConfigs()).thenReturn(canaryConfigs);

    ConfigExpressionFunctionProvider provider =
        new ConfigExpressionFunctionProvider(kayentaService);
    provider.acaNameToId("someothername", "myapp");
  }
}
