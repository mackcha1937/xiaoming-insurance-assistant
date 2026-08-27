package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.DocumentType;

import java.time.Instant;
import java.util.List;

public final class Product36743SourceCatalog {

    public static final String PRODUCT_ID = "36743";
    public static final String KNOWLEDGE_VERSION = "knowledge-36743-2026-08-27";
    public static final String RULES_VERSION = "V1-202508+TBXZSV-202604";
    public static final String OCCUPATION_VERSION = "template-1963059331068178434";
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-27T00:00:00Z");

    private Product36743SourceCatalog() {
    }

    public static List<SourceMaterialSpec> sources() {
        return List.of(
                spec("doc-01", "01-主险条款.pdf", DocumentType.POLICY_TERMS,
                        "f167b36b15c46fd1e6eca8b5bbce851d9c6e5bea09446b1adccfccfc1a055f27", 100),
                spec("doc-02", "02-主险费率表.xlsx", DocumentType.RATE_TABLE,
                        "beb5bc2bd255f0bd473add635cdfe193bb247a1691fdd4de8fc17793628a9336", 70),
                spec("doc-03", "03-主险产品说明书.pdf", DocumentType.POLICY_TERMS,
                        "6e8366ce497c2e7a7344c8a41bc37fdf3c3bd92ccf14a5241029bc771140dc74", 80),
                spec("doc-04", "04-主险免责条款.pdf", DocumentType.POLICY_TERMS,
                        "54f68d81f2f77c31b7dd56928dd068e8d61c4563ccb0417515a6fa5357bd0fc9", 95),
                spec("doc-05", "05-主险现金价值表.xlsx", DocumentType.CASH_VALUE_TABLE,
                        "5eb7f53889b909b375c13fbf02b8e842fab86774befa48e3a432582f684360fa", 60),
                spec("doc-06", "06-投保规则.pdf", DocumentType.APPLICATION_RULES,
                        "3121b06b75c462387c6d1c8e285d7c25f28b20efd07877776059bcc96386c9e8", 110),
                spec("doc-07", "07-投保须知书.pdf", DocumentType.APPLICATION_GUIDE,
                        "c7b0bf3f60dea9abbca336861fdadde3685697e88ed611658b1249da89f2a2c1", 105),
                spec("doc-08", "08-投保提示书-重庆版.pdf", DocumentType.APPLICATION_GUIDE,
                        "5136599a9a999c670f72d30fe51e435bf33cb47be7658612ce6f73df4551d731", 85),
                spec("doc-09", "09-投保提示书-全国版.pdf", DocumentType.APPLICATION_GUIDE,
                        "70da043f5f463f323cb7fdabdabd4166bbebbdd8d761e5dd442d68d25a5699fa", 85),
                spec("doc-10", "10-人身保险投保提示书.pdf", DocumentType.APPLICATION_GUIDE,
                        "512230fd733811b697b557eeb925a3f5664ac108400cefbf2a07fbdf8ae4649a", 75),
                spec("doc-11", "11-保额规则.pdf", DocumentType.APPLICATION_RULES,
                        "e8aa6883b596aa9b2945dd01125cbeb090f375f59d915985702813cbb7ce17b8", 115),
                spec("doc-12", "12-缴费保障期限年龄上限.pdf", DocumentType.APPLICATION_RULES,
                        "f9e3ba9ffee479c91cf410cb73314969a4eb03dd6efdb14475e975af9a0a36d8", 115),
                spec("doc-13", "13-保障期限可投责任.pdf", DocumentType.APPLICATION_RULES,
                        "ab50f2306c2a60e3c22340ffb43a4254c9b75e18b9051cb4ef9cc9d31fc59aed", 115),
                spec("doc-14", "14-投保人豁免险条款.pdf", DocumentType.POLICY_TERMS,
                        "fe955aebedb8666c435a74a7ac7152bb1220affe2a11d1d9891617457463a5f0", 90),
                spec("doc-15", "15-投保人豁免险产品说明书.pdf", DocumentType.POLICY_TERMS,
                        "ccf021f82ca01c51b5513f2035cf1e87239e37dbf469e88313a130e4558dbb4c", 75),
                spec("doc-16", "16-投保人豁免险费率表.xls", DocumentType.RATE_TABLE,
                        "e45efceb9c2cbd04fc7e911b876fc81dc33a30e5610aeee24e99b975662fd7fe", 65),
                spec("doc-17", "17-投保人豁免险免责条款.pdf", DocumentType.POLICY_TERMS,
                        "752d9bba3aec7581227c4c637b3d77f4c1886386f638c5b24bb5a1a8c229f07c", 90),
                spec("doc-18", "18-投保人豁免险现金价值表.xlsx", DocumentType.CASH_VALUE_TABLE,
                        "0996eb74636ba2f282f2ab03448687c3e37a026e1b51c6c53c495a47569024f3", 55),
                spec("doc-19", "19-职业分类.json", DocumentType.OCCUPATION_TABLE,
                        "6b85112b8e0bf2cdd8987dc290572ffe1580236cbe6280935156de87647a136a", 100),
                spec("doc-20", "20-产品详情页.txt", DocumentType.APPLICATION_GUIDE,
                        "64dcb4e772bade232578bef96f5d211d8c88e6bb149cf3564b84cf055958b2d5", 70),
                spec("doc-21", "21-健康告知预览.txt", DocumentType.HEALTH_DISCLOSURE,
                        "e290fd04657404cddf59aa889c1819e1d222ccd80fb7af91455c8efb6e90e5a6", 80)
        );
    }

    private static SourceMaterialSpec spec(
            String documentId,
            String fileName,
            DocumentType type,
            String sha256,
            int priority
    ) {
        return new SourceMaterialSpec(documentId, fileName, type, sha256, CONFIRMED_AT, priority);
    }
}
