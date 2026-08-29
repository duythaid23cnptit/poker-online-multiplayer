package com.ptit.poker.analytics.application.timebucket;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("!bootstrap") @ConfigurationProperties(prefix="poker.analytics")
public class AnalyticsProperties {
    private ZoneId zoneId=ZoneId.of("Asia/Bangkok");
    public ZoneId getZoneId(){return zoneId;} public void setZoneId(ZoneId zoneId){this.zoneId=zoneId;}
}
