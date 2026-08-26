package com.embabel.insurance.xiaoming.contract;

import java.util.List;

public record MissingField(
        String name,
        String label,
        String inputType,
        boolean required,
        List<String> options
) {
    public MissingField {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
