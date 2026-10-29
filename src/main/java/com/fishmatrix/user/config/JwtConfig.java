package com.fishmatrix.user.config;

import com.fishmatrix.common.auth.JwtIssuer;
import com.fishmatrix.common.auth.JwtVerifier;
import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户中台是唯一持有签发私钥的服务；其它服务和网关只用公钥验签。
 *
 * <p>这里没有启用 tf-common 的 {@code FishAuthAutoConfiguration}（{@code fm.auth.enabled=false}，
 * 避免自调用），只单独装配签发器与验签器。</p>
 */
@Configuration
@EnableConfigurationProperties(FishAuthProperties.class)
public class JwtConfig {

    @Bean
    public JwtIssuer jwtIssuer(FishAuthProperties properties) {
        return new JwtIssuer(properties);
    }

    @Bean
    public JwtVerifier jwtVerifier(FishAuthProperties properties) {
        return new JwtVerifier(properties);
    }
}
