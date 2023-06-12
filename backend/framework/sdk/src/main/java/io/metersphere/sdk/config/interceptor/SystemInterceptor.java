package io.metersphere.sdk.config.interceptor;

import io.metersphere.sdk.util.CompressUtils;
import io.metersphere.sdk.util.MybatisInterceptorConfig;
import io.metersphere.system.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SystemInterceptor {
    @Bean
    public List<MybatisInterceptorConfig> systemCompressConfigs() {
        List<MybatisInterceptorConfig> configList = new ArrayList<>();

        configList.add(new MybatisInterceptorConfig(TestResource.class, "configuration", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(AuthSource.class, "configuration", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(NoviceStatistics.class, "dataOption", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(PluginBlob.class, "formOption", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(PluginBlob.class, "formScript", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(ServiceIntegration.class, "configuration", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(UserExtend.class, "platformInfo", CompressUtils.class, "zip", "unzip"));

        return configList;
    }
}
