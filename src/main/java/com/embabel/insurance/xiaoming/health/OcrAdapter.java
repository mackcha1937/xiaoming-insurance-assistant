package com.embabel.insurance.xiaoming.health;

import java.nio.file.Path;

public interface OcrAdapter {
    String extract(Path image, String mediaType);
}
