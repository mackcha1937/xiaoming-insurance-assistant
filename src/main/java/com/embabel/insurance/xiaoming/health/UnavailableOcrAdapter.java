package com.embabel.insurance.xiaoming.health;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class UnavailableOcrAdapter implements OcrAdapter {
    @Override
    public String extract(Path image, String mediaType) {
        throw new HealthServiceException(
                HealthServiceException.Reason.FILE_UNSUPPORTED,
                "当前 OCR 服务不可用，请改用可解析 PDF 或手工文字输入。",
                ""
        );
    }
}
