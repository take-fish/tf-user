package com.fishmatrix.user.controller;

import com.fishmatrix.user.model.AppMeta;
import com.fishmatrix.user.service.AppMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class MetaController {

    private final AppMetaService meta;

    /**
     * 公开的运行环境信息，只暴露环境名、数据库名、命名空间和版本，不包含凭据。
     */
    @GetMapping("/meta")
    public AppMeta meta() {
        return meta.current();
    }
}
