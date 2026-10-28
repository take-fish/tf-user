package com.fishmatrix.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fishmatrix.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
class MybatisPlusConfigTest {

    @Autowired
    private MetaObjectHandler metaObjectHandler;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Test
    @DisplayName("自动填充处理器与 Mapper 都已注册")
    void beansRegistered() {
        assertThat(metaObjectHandler).isNotNull();
        assertThat(userAccountMapper).isNotNull();
    }
}
