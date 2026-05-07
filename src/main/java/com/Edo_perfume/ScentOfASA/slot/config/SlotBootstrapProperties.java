package com.Edo_perfume.ScentOfASA.slot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.slot-bootstrap")
public class SlotBootstrapProperties {

    private boolean enabled;
    private int monthsAhead = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMonthsAhead() {
        return monthsAhead;
    }

    public void setMonthsAhead(int monthsAhead) {
        this.monthsAhead = monthsAhead;
    }
}
