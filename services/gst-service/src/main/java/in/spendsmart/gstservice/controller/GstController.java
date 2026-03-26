package in.spendsmart.gstservice.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.spendsmart.gstservice.service.GstCalculatorService;
import in.spendsmart.gstservice.service.GstExportService;
import in.spendsmart.gstservice.service.GstinValidationService;
import in.spendsmart.gstservice.service.Gstr2bReconciliationService;
import in.spendsmart.gstservice.service.TallyExportService;

@RestController
@RequestMapping("/v1/gst")
public class GstController {

    private final GstinValidationService gstinValidationService;
    private final GstCalculatorService gstCalculatorService;
    private final Gstr2bReconciliationService gstr2bReconciliationService;
    private final GstExportService gstExportService;
    private final TallyExportService tallyExportService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GstController(
            GstinValidationService gstinValidationService,
            GstCalculatorService gstCalculatorService,
            Gstr2bReconciliationService gstr2bReconciliationService,
            GstExportService gstExportService,
            TallyExportService tallyExportService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.gstinValidationService = gstinValidationService;
        this.gstCalculatorService = gstCalculatorService;
        this.gstr2bReconciliationService = gstr2bReconciliationService;
        this.gstExportService = gstExportService;
        this.tallyExportService = tallyExportService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/validate")
    public GstinValidationService.GstinResult validate(@RequestParam("gstin") String gstin) {
        return gstinValidationService.validateGstin(gstin);
    }

    @PostMapping("/calculate")
    public GstCalculatorService.GstResult calculate(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("gstRate") BigDecimal gstRate,
            @RequestParam("supplierStateCode") String supplierStateCode,
            @RequestParam("buyerStateCode") String buyerStateCode
    ) {
        return gstCalculatorService.computeGst(amount, gstRate, supplierStateCode, buyerStateCode);
    }

    @PostMapping(value = "/reconcile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Gstr2bReconciliationService.ReconciliationReport reconcile(
            @RequestParam("orgId") UUID orgId,
            @RequestParam("period") String period,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        String gstr2bJson = new String(file.getBytes(), StandardCharsets.UTF_8);
        Gstr2bReconciliationService.ReconciliationReport report =
                gstr2bReconciliationService.reconcile(orgId, period, gstr2bJson);
        cacheReconciliationReport(orgId, period, report);
        return report;
    }

    @GetMapping("/export/xlsx")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam("orgId") UUID orgId,
            @RequestParam("period") String period
    ) {
        byte[] workbook = gstExportService.generateGstReport(orgId, period);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("gst-report-" + period + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(workbook);
    }

    @GetMapping("/export/tally")
    public ResponseEntity<String> exportTally(
            @RequestParam("orgId") UUID orgId,
            @RequestParam("period") String period
    ) {
        String tallyXml = tallyExportService.generateTallyXml(orgId, period);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.attachment().filename("gst-report-" + period + ".xml").build());
        return ResponseEntity.ok().headers(headers).body(tallyXml);
    }

    private void cacheReconciliationReport(
            UUID orgId,
            String period,
            Gstr2bReconciliationService.ReconciliationReport report
    ) {
        try {
            String key = "gstr2b:reconciliation:" + orgId + ":" + period;
            String json = objectMapper.writeValueAsString(report);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException ignored) {
        }
    }
}