package com.Edo_perfume.ScentOfASA.slot.config;

import java.time.YearMonth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.Edo_perfume.ScentOfASA.slot.service.AdminSlotService;

@Component
@ConditionalOnProperty(name = "app.slot-bootstrap.enabled", havingValue = "true")
public class RenderSlotBootstrapRunner implements ApplicationRunner {

    private final AdminSlotService adminSlotService;
    private final SlotBootstrapProperties properties;

    public RenderSlotBootstrapRunner(AdminSlotService adminSlotService,
                                     SlotBootstrapProperties properties) {
        this.adminSlotService = adminSlotService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        int monthsAhead = Math.max(0, properties.getMonthsAhead());
        YearMonth currentMonth = YearMonth.now();
        for (int offset = 0; offset <= monthsAhead; offset++) {
            YearMonth targetMonth = currentMonth.plusMonths(offset);
            adminSlotService.ensureMonthlySlots(targetMonth.getYear(), targetMonth.getMonthValue());
        }
    }
}
