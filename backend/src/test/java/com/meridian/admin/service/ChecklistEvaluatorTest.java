package com.meridian.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.Document;
import com.meridian.admin.domain.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChecklistEvaluatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @ParameterizedTest
    @CsvSource({
            "TX-1234567,   TX, PASS",
            "TX-123456,    TX, PASS",
            "TX-12345678,  TX, PASS",
            "TX-12345,     TX, FAIL",
            "TX-123456789, TX, FAIL",
            "tx-1234567,   TX, FAIL",
            "TX1234567,    TX, FAIL",
            "OK-1234567,   TX, FAIL"
    })
    void licenseFormat(String number, String state, CheckResult expected) {
        assertThat(ChecklistEvaluator.licenseFormat(number, state).result()).isEqualTo(expected);
    }

    @Test
    void licenseFormatRejectsBlank() {
        assertThat(ChecklistEvaluator.licenseFormat("  ", "TX").result()).isEqualTo(CheckResult.FAIL);
    }

    @Test
    void licenseExpiringTomorrowPassesButExpiringTodayFails() {
        assertThat(ChecklistEvaluator.licenseNotExpired(TODAY.plusDays(1), TODAY).result()).isEqualTo(CheckResult.PASS);
        assertThat(ChecklistEvaluator.licenseNotExpired(TODAY, TODAY).result()).isEqualTo(CheckResult.FAIL);
        assertThat(ChecklistEvaluator.licenseNotExpired(TODAY.minusDays(30), TODAY).result()).isEqualTo(CheckResult.FAIL);
    }

    @Test
    void documentsMustIncludeEveryRequiredType() {
        assertThat(ChecklistEvaluator.documentsUploaded(List.of()).result()).isEqualTo(CheckResult.FAIL);

        var partial = ChecklistEvaluator.documentsUploaded(
                List.of(doc(DocumentType.PHARMACY_LICENSE), doc(DocumentType.OWNER_ID)));
        assertThat(partial.result()).isEqualTo(CheckResult.FAIL);
        assertThat(partial.detail()).contains("Business registration").doesNotContain("Owner ID");

        var all = ChecklistEvaluator.documentsUploaded(List.of(doc(DocumentType.PHARMACY_LICENSE),
                doc(DocumentType.BUSINESS_REGISTRATION), doc(DocumentType.OWNER_ID)));
        assertThat(all.result()).isEqualTo(CheckResult.PASS);
    }

    @Test
    void deliveryRadiusMustBeSetPositiveAndWithinLimit() {
        assertThat(ChecklistEvaluator.deliveryRadius(null).result()).isEqualTo(CheckResult.FAIL);
        assertThat(ChecklistEvaluator.deliveryRadius(BigDecimal.ZERO).result()).isEqualTo(CheckResult.FAIL);
        assertThat(ChecklistEvaluator.deliveryRadius(new BigDecimal("0.5")).result()).isEqualTo(CheckResult.PASS);
        assertThat(ChecklistEvaluator.deliveryRadius(new BigDecimal("50")).result()).isEqualTo(CheckResult.PASS);
        assertThat(ChecklistEvaluator.deliveryRadius(new BigDecimal("50.1")).result()).isEqualTo(CheckResult.FAIL);
    }

    private static Document doc(DocumentType type) {
        return new Document(null, type, "file.pdf", "application/pdf", 1000);
    }
}
