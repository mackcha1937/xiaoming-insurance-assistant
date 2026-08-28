package com.embabel.insurance.xiaoming.health;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record HealthExtractCommand(
        String productId,
        String conversationId,
        String knowledgeVersion,
        boolean authorized,
        String text,
        List<MultipartFile> files
) {
    public HealthExtractCommand {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
