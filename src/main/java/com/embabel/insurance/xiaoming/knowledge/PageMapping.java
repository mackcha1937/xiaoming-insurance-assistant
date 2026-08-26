package com.embabel.insurance.xiaoming.knowledge;

public record PageMapping(int pdfPage, Integer printedPage) {

    public PageMapping {
        if (pdfPage < 1) {
            throw new IllegalArgumentException("pdfPage must be positive");
        }
        if (printedPage != null && printedPage < 1) {
            throw new IllegalArgumentException("printedPage must be positive when present");
        }
    }
}
