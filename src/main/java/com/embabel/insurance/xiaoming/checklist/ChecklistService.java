package com.embabel.insurance.xiaoming.checklist;

import com.embabel.insurance.xiaoming.api.ChecklistGenerateRequest;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChecklistService {

    private static final String PRODUCT_ID = "36743";
    private static final String RULE_VERSION = "material-checklist-v1";

    private final KnowledgeSnapshotStore snapshotStore;

    public ChecklistService(KnowledgeSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public ChecklistResult generate(ChecklistGenerateRequest request) {
        KnowledgeSnapshotStore.Snapshot snapshot = snapshotStore.current().orElseThrow(() ->
                new ChecklistServiceException(
                        ChecklistServiceException.Reason.SNAPSHOT_UNAVAILABLE,
                        "当前没有可用的已发布资料版本，无法生成清单。",
                        ""
                ));
        String currentVersion = snapshot.knowledgeVersion().versionId();
        if (!PRODUCT_ID.equals(request.getProductId())) {
            throw new IllegalArgumentException("当前仅支持产品 36743");
        }
        if (!currentVersion.equals(request.getKnowledgeVersion())) {
            throw new ChecklistServiceException(
                    ChecklistServiceException.Reason.VERSION_CONFLICT,
                    "资料版本已更新，请按最新资料重新生成清单。",
                    currentVersion
            );
        }

        List<ChecklistItem> items = new ArrayList<>();
        add(items, "applicant-information", ChecklistSection.INPUT_INFORMATION,
                "准备投保人基本信息", true, "正式投保页面需要投保人按页面要求如实录入。 ");
        add(items, "insured-information", ChecklistSection.INPUT_INFORMATION,
                "准备被保险人基本信息", true, "用于确认被保险人与投保方案。 ");
        add(items, "contact-information", ChecklistSection.INPUT_INFORMATION,
                "准备有效联系方式与联系地址", true, "仅在正式投保页面录入，不要在本 POC 中填写。 ");

        add(items, "applicant-id", ChecklistSection.PREPARE_MATERIALS,
                idLabel("投保人", request.getIdDocumentType()), true, "用于正式页面身份核验。 ");
        if (!"SELF".equals(request.getApplicantRelationship())) {
            add(items, "insured-id", ChecklistSection.PREPARE_MATERIALS,
                    idLabel("被保险人", request.getIdDocumentType()), true, "非本人投保需分别核对身份。 ");
            add(items, "relationship-proof", ChecklistSection.PREPARE_MATERIALS,
                    "准备投保人与被保险人关系证明", true, "非本人投保时按正式页面要求核对关系。 ");
        }
        if (request.getInsuredAgeYears() < 18) {
            add(items, "guardian-proof", ChecklistSection.PREPARE_MATERIALS,
                    "准备监护关系证明", true, "未成年人投保需由合法监护人办理。 ");
        }
        if (!"NATIONAL_ID".equals(request.getIdDocumentType())) {
            add(items, "document-validity", ChecklistSection.PREPARE_MATERIALS,
                    "核对证件有效期与辅助身份证明", true, "非居民身份证场景以正式页面要求为准。 ");
        }
        if ("DESIGNATED".equals(request.getBeneficiaryMode())) {
            add(items, "beneficiary-information", ChecklistSection.INPUT_INFORMATION,
                    "准备指定受益人信息", true, "指定受益人时需在正式页面如实录入。 ");
            add(items, "beneficiary-proof", ChecklistSection.PREPARE_MATERIALS,
                    "准备受益人身份证明及关系证明", true, "具体材料以正式投保页面要求为准。 ");
        } else {
            add(items, "beneficiary-review", ChecklistSection.INPUT_INFORMATION,
                    "确认是否采用法定受益人", false, "建议投保前再次确认受益人安排。 ");
        }
        if ("NEEDS_DISCLOSURE".equals(request.getHealthStatus())) {
            add(items, "truthful-health-disclosure", ChecklistSection.INPUT_INFORMATION,
                    "正式投保时如实完成健康告知", true,
                    "这里只提示如实告知，不生成补件规则，也不作核保结论。 ");
        }

        add(items, "read-product-terms", ChecklistSection.READ_CONFIRM,
                "阅读保险条款与疾病定义", true, "重点核对保障责任、责任免除和疾病定义。 ");
        add(items, "read-application-notice", ChecklistSection.READ_CONFIRM,
                "阅读投保须知与产品说明", true, "确认投保条件、流程和重要提示。 ");
        add(items, "read-privacy", ChecklistSection.READ_CONFIRM,
                "阅读隐私与个人信息处理说明", true, "了解正式投保页面的数据处理范围。 ");
        add(items, "review-sample-policy", ChecklistSection.READ_CONFIRM,
                "提前查看电子保单样式与服务说明", false, "建议项，不影响本清单其他必需项。 ");

        return new ChecklistResult(
                RULE_VERSION + "@" + currentVersion,
                currentVersion,
                items,
                "清单仅用于投保前准备；实际录入和材料要求以正式投保页面及保险公司审核为准。"
        );
    }

    private String idLabel(String role, String idDocumentType) {
        return switch (idDocumentType) {
            case "PASSPORT" -> "准备" + role + "有效护照";
            case "OTHER" -> "准备" + role + "有效身份证明";
            default -> "准备" + role + "有效居民身份证";
        };
    }

    private void add(
            List<ChecklistItem> items,
            String id,
            ChecklistSection section,
            String label,
            boolean required,
            String reason
    ) {
        items.add(new ChecklistItem(id, section, label, required, reason.trim()));
    }
}
