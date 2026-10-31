
package com.example.demo.config;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zc.api.APIConstants.ZCUserScope;
import com.zc.auth.ZCAuth;
import com.zc.common.ZCProject;
import com.zc.common.ZCProjectConfig;

@Configuration
@Profile("local")
public class CatalystLocalConfig {

    @Value("${CATALYST_CLIENT_ID}")
    private String clientId;

    @Value("${CATALYST_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${CATALYST_REFRESH_TOKEN}")
    private String refreshToken;

    @Value("${CATALYST_ZAID}")
    private String zaid;

    @Value("${CATALYST_PROJECT_ID}")
    private Long projectId;

    @Bean
    public ZCProject catalystProject() {

        JSONObject oauthParams = new JSONObject();

        oauthParams.put("client_id", clientId);
        oauthParams.put("client_secret", clientSecret);
        oauthParams.put("refresh_token", refreshToken);
        oauthParams.put("grant_type", "refresh_token");

        ZCAuth auth = ZCAuth.getInstance(oauthParams);
        auth.setScope(ZCUserScope.ADMIN);

        ZCProjectConfig config = ZCProjectConfig.newBuilder()
                .setProjectId(projectId)
                .setProjectKey(zaid)
                .setZcAuth(auth)
                .setProjectDomain("https://api.catalyst.zoho.com")
                .setEnvironment("Development")
                .build();

        ZCProject project = ZCProject.initProject(config, "");

        System.out.println("Catalyst local SDK initialized successfully");

        return project;
    }
}
