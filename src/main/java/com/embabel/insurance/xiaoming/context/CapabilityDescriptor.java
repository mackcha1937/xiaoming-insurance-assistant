package com.embabel.insurance.xiaoming.context;

import com.embabel.insurance.xiaoming.contract.XiaomingIntent;

public record CapabilityDescriptor(
        XiaomingIntent intent,
        String label,
        boolean enabled
) {
}
