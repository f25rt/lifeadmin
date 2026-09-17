package com.lifeadmin.dashboard.api;

import com.lifeadmin.dashboard.DashboardService;
import com.lifeadmin.dashboard.api.DashboardDtos.DashboardResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home dashboard API (spec §5). Requires authentication; scoped to the caller's account.
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse get() {
        return dashboardService.get();
    }
}
