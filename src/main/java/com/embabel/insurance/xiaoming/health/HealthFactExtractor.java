package com.embabel.insurance.xiaoming.health;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HealthFactExtractor {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern NAMED_PERSON = Pattern.compile("[\\p{IsHan}]{1,4}(先生|女士)");
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[年./-](\\d{1,2})(?:[月./-](\\d{1,2})日?)?");
    private static final Pattern YEARS_AGO = Pattern.compile("(\\d{1,2})年前");
    private static final Pattern MONTHS_AGO = Pattern.compile("(\\d{1,2})个月前");
    private static final Pattern BMI = Pattern.compile("(?i)BMI\\s*(?:值)?[：:=为]?\\s*(\\d{2}(?:\\.\\d+)?)");
    private static final Pattern HEIGHT = Pattern.compile("身高\\s*(\\d{2,3}(?:\\.\\d+)?)\\s*(cm|厘米|米|m)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT = Pattern.compile("体重\\s*(\\d{2,3}(?:\\.\\d+)?)\\s*(kg|公斤|千克|斤)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT_LOSS = Pattern.compile("(?:体重)?(?:下降|减轻|减少)\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|公斤|千克|斤)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATION = Pattern.compile("(?:无|未|否认|没有|不曾|从未|并无|未曾|未见|排除)$");

    private static final List<Term> TERMS = List.of(
            term("HOSPITALIZATION", "住院", HealthFactType.HOSPITALIZATION, "住院"),
            term("SURGERY", "手术", HealthFactType.SURGERY, "手术"),
            term("HYPERTENSION", "高血压", HealthFactType.DISEASE, "高血压"),
            term("DIABETES", "糖尿病或高血糖", HealthFactType.DISEASE, "糖尿病|高血糖|糖耐量异常"),
            term("MALIGNANT_TUMOR", "肿瘤相关疾病", HealthFactType.DISEASE, "恶性肿瘤|白血病|淋巴瘤|原位癌|癌前病变"),
            term("NODULE", "结节或占位", HealthFactType.DISEASE, "甲状腺结节|乳腺结节|肺结节|结节|肿块|包块|占位|赘生物"),
            term("CARDIOVASCULAR", "心脑血管疾病", HealthFactType.DISEASE, "冠心病|心肌梗塞|心肌缺血|脑出血|脑缺血|心功能不全|心瓣膜病"),
            term("RESPIRATORY", "呼吸系统疾病", HealthFactType.DISEASE, "哮喘|慢性阻塞性肺病|支气管扩张|肺纤维化|肺结核|肺栓塞|呼吸衰竭"),
            term("NEUROLOGICAL", "神经或精神疾病", HealthFactType.DISEASE, "癫痫|帕金森|抑郁症|焦虑症|精神分裂症|多发性硬化|重症肌无力"),
            term("LIVER", "肝脏疾病", HealthFactType.DISEASE, "慢性肝炎|乙肝病毒携带|丙型肝炎|肝硬化|肝功能衰竭"),
            term("KIDNEY", "肾脏疾病", HealthFactType.DISEASE, "肾炎|肾积水|肾功能不全|肾病综合症|多囊肾|尿毒症"),
            term("AUTOIMMUNE", "免疫或骨骼肌肉疾病", HealthFactType.DISEASE, "类风湿关节炎|系统性红斑狼疮|硬皮病|强直性脊柱炎|痛风"),
            term("SYMPTOM_HEADACHE", "不明原因头痛", HealthFactType.SYMPTOM, "头痛"),
            term("SYMPTOM_CHEST_PAIN", "不明原因胸痛", HealthFactType.SYMPTOM, "胸痛"),
            term("SYMPTOM_BLEEDING", "不明原因出血", HealthFactType.SYMPTOM, "咯血|便血|血尿|皮下出血|紫癜"),
            term("SYMPTOM_FEVER", "持续发热", HealthFactType.SYMPTOM, "持续发热|发热超过4周"),
            term("ABNORMAL_EXAM", "检查异常", HealthFactType.EXAMINATION,
                    "检查异常|血常规异常|尿常规异常|心电图异常|肝功能异常|肾功能异常|病理异常|活检异常|CT异常|MRI异常|超声异常|HPV阳性|TCT异常"),
            term("MEDICATION", "用药情况", HealthFactType.MEDICATION, "服药|用药|口服.*药|注射.*药"),
            term("FOLLOW_UP", "复查情况", HealthFactType.FOLLOW_UP, "复查|随访"),
            term("LOW_BIRTH_WEIGHT", "低出生体重", HealthFactType.BIRTH_HISTORY, "出生时体重.*低于?2\\.5|低出生体重"),
            term("PREMATURE_BIRTH", "早产等出生情况", HealthFactType.BIRTH_HISTORY, "早产|难产|出生窒息|颅内出血|出生缺氧"),
            term("DEVELOPMENTAL_DELAY", "发育或功能异常", HealthFactType.BIRTH_HISTORY, "发育迟缓|智力低下|脑瘫|惊厥"),
            term("FEMALE_CONDITION", "女性健康异常", HealthFactType.PREGNANCY, "阴道不规则出血|宫颈上皮内瘤变|子宫内膜异位症|卵巢囊肿|多囊卵巢|子宫肌瘤|HPV阳性"),
            term("PREGNANCY_RISK", "妊娠相关情况", HealthFactType.PREGNANCY, "怀孕28周|妊娠并发症|怀孕.*35周岁"),
            term("HIGH_RISK_ACTIVITY", "高风险活动", HealthFactType.ACTIVITY, "潜水|跳伞|滑翔|攀岩|探险|武术比赛|摔跤比赛|特技表演|赛马|赛车|私人性质飞行|战乱")
    );

    private final Clock clock;

    public HealthFactExtractor() {
        this(Clock.systemDefaultZone());
    }

    HealthFactExtractor(Clock clock) {
        this.clock = clock;
    }

    public List<HealthFactDraft> extract(List<SourceText> sources) {
        Map<String, HealthFactDraft> facts = new LinkedHashMap<>();
        for (SourceText source : sources) {
            String[] lines = source.text().split("\\R", -1);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                for (String rawSentence : lines[lineIndex].split("(?<=[。！？；;])")) {
                    String sentence = redact(rawSentence).trim();
                    if (sentence.isBlank()) continue;
                    LocalDate date = parseDate(sentence);
                    for (Term term : TERMS) {
                        Matcher matcher = term.pattern().matcher(sentence);
                        while (matcher.find()) {
                            HealthFactPolarity polarity = polarity(sentence, matcher.start());
                            add(facts, source, lineIndex + 1, sentence, date, term.type(), term.code(),
                                    term.label(), null, null, polarity);
                        }
                    }
                    extractMeasurements(facts, source, lineIndex + 1, sentence, date);
                    if (sentence.matches(".*(?:目前|当前).*(?:已治愈|已康复|仍有|持续|稳定|无异常).*$")) {
                        add(facts, source, lineIndex + 1, sentence, date, HealthFactType.CURRENT_STATUS,
                                "CURRENT_STATUS", "当前状态", null, null, HealthFactPolarity.PRESENT);
                    }
                }
            }
        }
        return List.copyOf(facts.values());
    }

    private void extractMeasurements(
            Map<String, HealthFactDraft> facts,
            SourceText source,
            int line,
            String sentence,
            LocalDate date
    ) {
        Matcher bmi = BMI.matcher(sentence);
        if (bmi.find()) {
            add(facts, source, line, sentence, date, HealthFactType.MEASUREMENT, "BMI", "BMI",
                    Double.parseDouble(bmi.group(1)), "kg/m²", HealthFactPolarity.PRESENT);
        } else {
            Matcher height = HEIGHT.matcher(sentence);
            Matcher weight = WEIGHT.matcher(sentence);
            if (height.find() && weight.find()) {
                double heightMeters = Double.parseDouble(height.group(1));
                if (!height.group(2).toLowerCase(Locale.ROOT).matches("m|米")) heightMeters /= 100.0;
                double kilograms = toKilograms(Double.parseDouble(weight.group(1)), weight.group(2));
                if (heightMeters > 0.5 && heightMeters < 2.5) {
                    double calculated = BigDecimal.valueOf(kilograms / (heightMeters * heightMeters))
                            .setScale(1, RoundingMode.HALF_UP).doubleValue();
                    add(facts, source, line, sentence, date, HealthFactType.MEASUREMENT, "BMI", "BMI",
                            calculated, "kg/m²", HealthFactPolarity.PRESENT);
                }
            }
        }

        Matcher loss = WEIGHT_LOSS.matcher(sentence);
        if (loss.find()) {
            add(facts, source, line, sentence, date, HealthFactType.MEASUREMENT,
                    "WEIGHT_LOSS", "体重减轻", toKilograms(Double.parseDouble(loss.group(1)), loss.group(2)),
                    "kg", HealthFactPolarity.PRESENT);
        }
    }

    private void add(
            Map<String, HealthFactDraft> facts,
            SourceText source,
            int line,
            String sentence,
            LocalDate date,
            HealthFactType type,
            String code,
            String label,
            Double numericValue,
            String unit,
            HealthFactPolarity polarity
    ) {
        String key = source.sourceId() + ':' + line + ':' + code + ':' + polarity;
        facts.putIfAbsent(key, new HealthFactDraft(
                "health-fact-" + UUID.randomUUID(),
                type,
                code,
                label,
                limit(sentence, 220),
                numericValue,
                unit,
                date,
                polarity,
                new HealthSourceLocation(source.sourceId(), source.sourceLabel(), source.page(), line, limit(sentence, 220)),
                source.confidence(),
                false
        ));
    }

    private HealthFactPolarity polarity(String sentence, int matchStart) {
        String prefix = sentence.substring(Math.max(0, matchStart - 8), matchStart)
                .replaceAll("[，,、\\s]", "");
        if (NEGATION.matcher(prefix).find()) return HealthFactPolarity.ABSENT;
        int clauseStart = 0;
        for (char separator : new char[]{'，', ',', '。', '；', ';', '！', '？'}) {
            clauseStart = Math.max(clauseStart, sentence.lastIndexOf(separator, Math.max(0, matchStart - 1)) + 1);
        }
        String clausePrefix = sentence.substring(clauseStart, matchStart).replaceAll("[、\\s]", "");
        return clausePrefix.matches("^(?:无|未|否认|没有|不曾|从未|并无|未曾|未见|排除).*")
                ? HealthFactPolarity.ABSENT
                : HealthFactPolarity.PRESENT;
    }

    private LocalDate parseDate(String sentence) {
        Matcher exact = DATE.matcher(sentence);
        if (exact.find()) {
            int year = Integer.parseInt(exact.group(1));
            int month = Integer.parseInt(exact.group(2));
            int day = exact.group(3) == null ? 1 : Integer.parseInt(exact.group(3));
            try {
                return LocalDate.of(year, month, day);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        Matcher years = YEARS_AGO.matcher(sentence);
        if (years.find()) return LocalDate.now(clock).minusYears(Integer.parseInt(years.group(1)));
        Matcher months = MONTHS_AGO.matcher(sentence);
        if (months.find()) return LocalDate.now(clock).minusMonths(Integer.parseInt(months.group(1)));
        if (sentence.contains("去年")) return YearMonth.now(clock).minusYears(1).atDay(1);
        return null;
    }

    private double toKilograms(double value, String unit) {
        return "斤".equals(unit) ? value / 2.0 : value;
    }

    private String redact(String value) {
        String redacted = PHONE.matcher(value).replaceAll("[手机号已移除]");
        redacted = ID_CARD.matcher(redacted).replaceAll("[证件号已移除]");
        return NAMED_PERSON.matcher(redacted).replaceAll("[称呼已移除]");
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static Term term(String code, String label, HealthFactType type, String regex) {
        return new Term(code, label, type, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    public record SourceText(
            String sourceId,
            String sourceLabel,
            Integer page,
            String text,
            double confidence
    ) {
    }

    private record Term(String code, String label, HealthFactType type, Pattern pattern) {
    }
}
