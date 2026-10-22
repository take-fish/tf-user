package com.fishmatrix.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户中台静态页入口。
 *
 * <p>静态资源位于 {@code classpath:/static/admin/}：直连 user 服务时访问 {@code /admin/}；
 * 经 Web 网关访问时使用 {@code /api/v1/user/admin/index.html}（由 WebConfig 映射）。</p>
 */
@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String adminPage() {
        return "redirect:/admin/index.html";
    }

    @GetMapping({"/api/v1/user/admin", "/api/v1/user/admin/"})
    public String proxiedAdminPage() {
        return "redirect:/api/v1/user/admin/index.html";
    }
}
