package com.guardian.laporan.controller;

import com.guardian.laporan.repository.LaporanRepository;
import com.guardian.laporan.repository.TokoRepository;
import com.guardian.laporan.service.ExcelReaderService;
import com.guardian.laporan.service.SupabaseStorageService;
import com.guardian.laporan.model.Laporan;
import com.guardian.laporan.model.Toko;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class StoreController {

    @Autowired
    private ExcelReaderService excelReaderService;
    @Autowired
    private LaporanRepository laporanRepository;
    @Autowired
    private TokoRepository tokoRepository;
    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @PostConstruct
    public void initDataToko() {
        if (tokoRepository.count() == 0) {
            String[][] dataAwal = {
                    {"6082", "GUARDIAN BALIKPAPAN SUPERBLOCK", "guardian_superblock@dfiretailgroup.com"},
                    {"6548", "GUARDIAN BALIKPAPAN PLAZA", "grdbppn@dfiretailgroup.com"},
                    {"6574", "GUARDIAN HERO BALIKPAPAN FANTASI 2", "guardian_hfantasy@dfiretailgroup.com"},
                    {"6820", "GUARDIAN LIVING PLAZA BALIKPAPAN", "gpbalikp@dfiretailgroup.com"},
                    {"6866", "GUARDIAN MALL PENTACITY SHOPPING VENUE", "guardian_nak6866@dfiretailgroup.com"}
            };
            for (String[] t : dataAwal) {
                Toko tokoBaru = new Toko();
                tokoBaru.setStoreCode(t[0]);
                tokoBaru.setStoreName(t[1]);
                tokoBaru.setEmail(t[2]);
                tokoRepository.save(tokoBaru);
            }
        }
    }

    @GetMapping("/")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/proses-login")
    public String prosesLogin(@RequestParam("username") String username,
                              @RequestParam(value = "password", required = false) String password,
                              HttpSession session) {
        if (username.equalsIgnoreCase("effi") && "123".equals(password)) {
            session.setAttribute("role", "AM");
            return "redirect:/dashboard-am";
        }

        String cleanStoreCode = username.replaceFirst("^0+(?!$)", "").trim();
        Toko toko = tokoRepository.findById(cleanStoreCode).orElse(null);
        if (toko != null) {
            session.setAttribute("storeCode", cleanStoreCode);
            session.setAttribute("role", "Store");
            return "redirect:/form-laporan";
        }
        return "redirect:/?error=Unauthorized";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // Helper Filter Tanggal (Mendukung Semua, Mingguan, Bulanan, dan Pilih Tanggal)
    private boolean isMatchDateFilter(LocalDate tanggalInput, String filter, String startDate, String endDate) {
        if (tanggalInput == null) return false;

        boolean hasStart = startDate != null && !startDate.trim().isEmpty();
        boolean hasEnd = endDate != null && !endDate.trim().isEmpty();

        if (hasStart || hasEnd) {
            try {
                if (hasStart) {
                    LocalDate start = LocalDate.parse(startDate.trim());
                    if (tanggalInput.isBefore(start)) return false;
                }
                if (hasEnd) {
                    LocalDate end = LocalDate.parse(endDate.trim());
                    if (tanggalInput.isAfter(end)) return false;
                }
                return true;
            } catch (Exception e) {
                return true;
            }
        }

        LocalDate now = LocalDate.now();
        if ("mingguan".equalsIgnoreCase(filter)) {
            return !tanggalInput.isBefore(now.minusDays(7));
        } else if ("bulanan".equalsIgnoreCase(filter)) {
            return !tanggalInput.isBefore(now.minusDays(30));
        }
        return true;
    }

    // Helper Format Teks Periode untuk Header Excel AM
    private String buildPeriodeLabel(String filter, String startDate, String endDate) {
        Locale idLocale = new Locale("id", "ID");
        DateTimeFormatter fullFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", idLocale);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d", idLocale);

        boolean hasStart = startDate != null && !startDate.trim().isEmpty();
        boolean hasEnd = endDate != null && !endDate.trim().isEmpty();

        try {
            if (hasStart && hasEnd) {
                LocalDate s = LocalDate.parse(startDate.trim());
                LocalDate e = LocalDate.parse(endDate.trim());
                if (s.getMonth() == e.getMonth() && s.getYear() == e.getYear()) {
                    return s.format(dayFmt) + " - " + e.format(fullFmt);
                }
                return s.format(fullFmt) + " - " + e.format(fullFmt);
            } else if (hasStart) {
                return "Mulai " + LocalDate.parse(startDate.trim()).format(fullFmt);
            } else if (hasEnd) {
                return "Sampai " + LocalDate.parse(endDate.trim()).format(fullFmt);
            } else if ("mingguan".equalsIgnoreCase(filter)) {
                LocalDate now = LocalDate.now();
                LocalDate s = now.minusDays(7);
                if (s.getMonth() == now.getMonth() && s.getYear() == now.getYear()) {
                    return s.format(dayFmt) + " - " + now.format(fullFmt);
                }
                return s.format(fullFmt) + " - " + now.format(fullFmt);
            } else if ("bulanan".equalsIgnoreCase(filter)) {
                LocalDate now = LocalDate.now();
                LocalDate s = now.minusDays(30);
                return s.format(fullFmt) + " - " + now.format(fullFmt);
            }
        } catch (Exception ignored) {
        }
        return "Semua Waktu";
    }

    // ==========================================
    // DASHBOARD AM - FILTER TANGGAL & REKAP LDC
    // ==========================================
    @GetMapping("/dashboard-am")
    public String dashboardAM(@RequestParam(value = "filter", defaultValue = "semua") String filter,
                              @RequestParam(value = "startDate", required = false) String startDate,
                              @RequestParam(value = "endDate", required = false) String endDate,
                              HttpSession session, Model model) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            filter = "custom";
        }

        List<Laporan> semuaLaporan = laporanRepository.findAll();
        List<Laporan> laporanFiltered = new ArrayList<>();

        for (Laporan l : semuaLaporan) {
            if (isMatchDateFilter(l.getTanggalInput(), filter, startDate, endDate)) {
                laporanFiltered.add(l);
            }
        }

        double totalWO = 0, totalMarkdown = 0;
        double totalLdcWO = 0, totalLdcMarkdown = 0;

        Map<String, List<Laporan>> mapLaporanPerToko = new HashMap<>();
        Map<String, Double> mapTotalPerToko = new HashMap<>();
        Map<String, Double> mapTotalLdcPerToko = new HashMap<>();

        for (Laporan l : laporanFiltered) {
            double hargaItem = l.getHarga() != null ? l.getHarga() : 0;
            double ldcItem = l.getLdc() != null ? l.getLdc() : 0;

            if ("Write Off".equalsIgnoreCase(l.getJenisAjuan())) {
                totalWO += hargaItem;
                totalLdcWO += ldcItem;
            } else if ("Markdown".equalsIgnoreCase(l.getJenisAjuan())) {
                totalMarkdown += hargaItem;
                totalLdcMarkdown += ldcItem;
            }

            String store = l.getStoreCode();
            mapLaporanPerToko.putIfAbsent(store, new ArrayList<>());
            mapLaporanPerToko.get(store).add(l);

            mapTotalPerToko.put(store, mapTotalPerToko.getOrDefault(store, 0.0) + hargaItem);
            mapTotalLdcPerToko.put(store, mapTotalLdcPerToko.getOrDefault(store, 0.0) + ldcItem);
        }

        Map<String, String> mapNamaToko = new HashMap<>();
        for (Toko t : tokoRepository.findAll()) {
            mapNamaToko.put(t.getStoreCode(), t.getStoreName());
        }

        model.addAttribute("totalWO", totalWO);
        model.addAttribute("totalMarkdown", totalMarkdown);
        model.addAttribute("totalLdcWO", totalLdcWO);
        model.addAttribute("totalLdcMarkdown", totalLdcMarkdown);
        model.addAttribute("mapLaporanPerToko", mapLaporanPerToko);
        model.addAttribute("mapTotalPerToko", mapTotalPerToko);
        model.addAttribute("mapTotalLdcPerToko", mapTotalLdcPerToko);
        model.addAttribute("mapNamaToko", mapNamaToko);
        model.addAttribute("listToko", tokoRepository.findAll());
        model.addAttribute("currentFilter", filter);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "dashboard-am";
    }

    @PostMapping("/am/tambah-toko")
    public String simpanToko(@RequestParam("storeCode") String storeCode,
                             @RequestParam("storeName") String storeName,
                             @RequestParam("email") String email, HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";
        Toko t = new Toko();
        t.setStoreCode(storeCode.trim());
        t.setStoreName(storeName.trim());
        t.setEmail(email.trim());
        tokoRepository.save(t);
        return "redirect:/dashboard-am";
    }

    @GetMapping("/am/hapus-toko/{id}")
    public String hapusToko(@PathVariable("id") String id, HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";
        tokoRepository.deleteById(id);
        return "redirect:/dashboard-am";
    }

    // ==========================================
    // EXPORT EXCEL AM (SHEET 1: SUMMARY APPROVED + SHEET 2: DETAIL)
    // ==========================================
    @GetMapping("/am/export-excel")
    public ResponseEntity<byte[]> exportToExcelAM(@RequestParam(value = "filter", defaultValue = "semua") String filter,
                                                  @RequestParam(value = "startDate", required = false) String startDate,
                                                  @RequestParam(value = "endDate", required = false) String endDate,
                                                  HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Laporan> filteredList = new ArrayList<>();
        for (Laporan lap : laporanRepository.findAll()) {
            if (isMatchDateFilter(lap.getTanggalInput(), filter, startDate, endDate)) {
                filteredList.add(lap);
            }
        }

        List<Toko> allToko = tokoRepository.findAll();
        Map<String, String> mapNamaToko = new HashMap<>();
        for (Toko t : allToko) {
            mapNamaToko.put(t.getStoreCode(), t.getStoreName());
        }

        // Hitung total WO & Markdown khusus status APPROVED per toko (berdasarkan LDC)
        Map<String, Double> approvedWoPerToko = new HashMap<>();
        Map<String, Double> approvedMdPerToko = new HashMap<>();

        for (Laporan lap : filteredList) {
            if ("Approved".equalsIgnoreCase(lap.getStatusApprove())) {
                String code = lap.getStoreCode();
                double nilaiLdc = lap.getLdc() != null ? lap.getLdc() : 0.0;

                if ("Write Off".equalsIgnoreCase(lap.getJenisAjuan())) {
                    approvedWoPerToko.put(code, approvedWoPerToko.getOrDefault(code, 0.0) + nilaiLdc);
                } else if ("Markdown".equalsIgnoreCase(lap.getJenisAjuan())) {
                    approvedMdPerToko.put(code, approvedMdPerToko.getOrDefault(code, 0.0) + nilaiLdc);
                }
            }
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            DataFormat dataFormat = workbook.createDataFormat();
            short numFmt = dataFormat.getFormat("#,##0");

            // --- STYLES UNTUK SHEET SUMMARY ---
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            Font normalFont = workbook.createFont();
            normalFont.setBold(false);

            CellStyle periodeStyle = workbook.createCellStyle();
            periodeStyle.setFont(normalFont);

            CellStyle goldHeaderStyle = workbook.createCellStyle();
            goldHeaderStyle.setFont(boldFont);
            goldHeaderStyle.setFillForegroundColor(IndexedColors.GOLD.getIndex());
            goldHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            goldHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            goldHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(goldHeaderStyle);

            CellStyle goldTotalNumStyle = workbook.createCellStyle();
            goldTotalNumStyle.setFont(boldFont);
            goldTotalNumStyle.setFillForegroundColor(IndexedColors.GOLD.getIndex());
            goldTotalNumStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            goldTotalNumStyle.setAlignment(HorizontalAlignment.RIGHT);
            goldTotalNumStyle.setDataFormat(numFmt);
            setThinBorders(goldTotalNumStyle);

            CellStyle centerCellStyle = workbook.createCellStyle();
            centerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            setThinBorders(centerCellStyle);

            CellStyle leftCellStyle = workbook.createCellStyle();
            leftCellStyle.setAlignment(HorizontalAlignment.LEFT);
            setThinBorders(leftCellStyle);

            CellStyle numCellStyle = workbook.createCellStyle();
            numCellStyle.setAlignment(HorizontalAlignment.RIGHT);
            numCellStyle.setDataFormat(numFmt);
            setThinBorders(numCellStyle);

            // ==========================================
            // SHEET 1: SUMMARY AREA (KHUSUS APPROVED)
            // ==========================================
            Sheet summarySheet = workbook.createSheet("Summary Area");

            // Baris 1: PERIODE
            Row rowPeriode = summarySheet.createRow(0);
            Cell cellPeriode = rowPeriode.createCell(0);
            cellPeriode.setCellValue("PERIODE : " + buildPeriodeLabel(filter, startDate, endDate));
            cellPeriode.setCellStyle(periodeStyle);

            // Baris 2: Header Tabel
            String[] summaryHeaders = {"No", "Code", "Store", "Ajuan WO", "Ajuan Markdown 50%", "Total"};
            int[] summaryWidths = {6, 10, 42, 18, 22, 18};
            Row rowHeader = summarySheet.createRow(1);
            rowHeader.setHeightInPoints(22);
            for (int i = 0; i < summaryHeaders.length; i++) {
                Cell c = rowHeader.createCell(i);
                c.setCellValue(summaryHeaders[i]);
                c.setCellStyle(goldHeaderStyle);
                summarySheet.setColumnWidth(i, summaryWidths[i] * 256);
            }

            // Baris 3 dst: Data Per Toko dari Master Toko
            int rowIdx = 2; // Index 2 = Baris 3 di Excel
            int noUrut = 1;
            for (Toko toko : allToko) {
                Row row = summarySheet.createRow(rowIdx);
                int excelRowNum = rowIdx + 1;

                String code = toko.getStoreCode();
                double woVal = approvedWoPerToko.getOrDefault(code, 0.0);
                double mdVal = approvedMdPerToko.getOrDefault(code, 0.0);

                Cell cNo = row.createCell(0);
                cNo.setCellValue(noUrut++);
                cNo.setCellStyle(centerCellStyle);

                Cell cCode = row.createCell(1);
                cCode.setCellValue(code);
                cCode.setCellStyle(centerCellStyle);

                Cell cStore = row.createCell(2);
                cStore.setCellValue(toko.getStoreName());
                cStore.setCellStyle(leftCellStyle);

                Cell cWo = row.createCell(3);
                cWo.setCellStyle(numCellStyle);
                if (woVal > 0) {
                    cWo.setCellValue(woVal);
                }

                Cell cMd = row.createCell(4);
                cMd.setCellStyle(numCellStyle);
                if (mdVal > 0) {
                    cMd.setCellValue(mdVal);
                }

                Cell cTotal = row.createCell(5);
                cTotal.setCellFormula("SUM(D" + excelRowNum + ":E" + excelRowNum + ")");
                cTotal.setCellStyle(numCellStyle);

                rowIdx++;
            }

            // Baris TOTAL di paling bawah
            int lastDataExcelRow = rowIdx; // Baris terakhir data di Excel (1-based)
            Row totalRow = summarySheet.createRow(rowIdx);
            totalRow.setHeightInPoints(22);

            for (int col = 0; col <= 2; col++) {
                Cell c = totalRow.createCell(col);
                if (col == 0) c.setCellValue("TOTAL");
                c.setCellStyle(goldHeaderStyle);
            }
            summarySheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 2));

            Cell cellTotalWo = totalRow.createCell(3);
            cellTotalWo.setCellStyle(goldTotalNumStyle);
            if (lastDataExcelRow >= 3) {
                cellTotalWo.setCellFormula("SUM(D3:D" + lastDataExcelRow + ")");
            } else {
                cellTotalWo.setCellValue(0);
            }

            Cell cellTotalMd = totalRow.createCell(4);
            cellTotalMd.setCellStyle(goldTotalNumStyle);
            if (lastDataExcelRow >= 3) {
                cellTotalMd.setCellFormula("SUM(E3:E" + lastDataExcelRow + ")");
            } else {
                cellTotalMd.setCellValue(0);
            }

            Cell cellGrandTotal = totalRow.createCell(5);
            cellGrandTotal.setCellStyle(goldTotalNumStyle);
            if (lastDataExcelRow >= 3) {
                cellGrandTotal.setCellFormula("SUM(F3:F" + lastDataExcelRow + ")");
            } else {
                cellGrandTotal.setCellValue(0);
            }

            // ==========================================
            // SHEET 2: DETAIL LAPORAN (RINCIAN ITEM)
            // ==========================================
            Sheet detailSheet = workbook.createSheet("Detail Laporan");
            populateDetailSheet(detailSheet, workbook, filteredList, mapNamaToko);

            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            String label = (startDate != null && !startDate.isEmpty()) ? startDate + "_sd_" + endDate : filter;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Summary_Laporan_Area_" + label + ".xlsx");
            headers.setContentLength(bytes.length);

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void populateDetailSheet(Sheet sheet, Workbook workbook, List<Laporan> listData, Map<String, String> mapNamaToko) {
        String[] columns = {
                "Tanggal", "Kode Toko", "Nama Toko", "PLU", "Nama Barang",
                "Expired Date", "Keterangan Rusak", "Jenis Ajuan",
                "LDC (Beli)", "Harga (Jual)", "Pengaju", "Status", "Reason Reject"
        };
        int[] colWidths = {14, 12, 32, 12, 35, 14, 28, 15, 16, 16, 18, 14, 30};

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, colWidths[i] * 256);
        }

        int rowNum = 1;
        for (Laporan lap : listData) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(lap.getTanggalInput() != null ? lap.getTanggalInput().toString() : "-");
            row.createCell(1).setCellValue(lap.getStoreCode() != null ? lap.getStoreCode() : "-");
            row.createCell(2).setCellValue(mapNamaToko.getOrDefault(lap.getStoreCode(), "Toko Tidak Dikenal"));
            row.createCell(3).setCellValue(lap.getPlu() != null ? lap.getPlu() : "-");
            row.createCell(4).setCellValue(lap.getNamaBarang() != null ? lap.getNamaBarang() : "-");
            row.createCell(5).setCellValue(lap.getExpiredDate() != null ? lap.getExpiredDate() : "-");
            row.createCell(6).setCellValue(lap.getKeterangan() != null ? lap.getKeterangan() : "-");
            row.createCell(7).setCellValue(lap.getJenisAjuan() != null ? lap.getJenisAjuan() : "-");
            row.createCell(8).setCellValue(lap.getLdc() != null ? lap.getLdc() : 0.0);
            row.createCell(9).setCellValue(lap.getHarga() != null ? lap.getHarga() : 0.0);
            row.createCell(10).setCellValue(lap.getNamaPengaju() != null ? lap.getNamaPengaju() : "-");
            row.createCell(11).setCellValue(lap.getStatusApprove() != null ? lap.getStatusApprove() : "-");
            row.createCell(12).setCellValue(lap.getRejectReason() != null ? lap.getRejectReason() : "-");
        }
    }

    // EXPORT EXCEL KHUSUS TOKO
    @GetMapping("/toko/export-excel")
    public ResponseEntity<byte[]> exportToExcelToko(@RequestParam(value = "startDate", required = false) String startDate,
                                                    @RequestParam(value = "endDate", required = false) String endDate,
                                                    HttpSession session) {
        String storeCode = (String) session.getAttribute("storeCode");
        if (storeCode == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Laporan> filteredList = new ArrayList<>();
        for (Laporan lap : laporanRepository.findAll()) {
            if (storeCode.equals(lap.getStoreCode()) &&
                isMatchDateFilter(lap.getTanggalInput(), "semua", startDate, endDate)) {
                filteredList.add(lap);
            }
        }

        Map<String, String> mapNamaToko = new HashMap<>();
        for (Toko t : tokoRepository.findAll()) {
            mapNamaToko.put(t.getStoreCode(), t.getStoreName());
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Rekap Laporan Toko");
            populateDetailSheet(sheet, workbook, filteredList, mapNamaToko);

            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Laporan_Toko_" + storeCode + ".xlsx");
            headers.setContentLength(bytes.length);

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // DOWNLOAD TEMPLATE KOSONG BERITA ACARA (TOKO)
    // ==========================================
    @GetMapping("/berita-acara/download-template")
    public ResponseEntity<byte[]> downloadTemplateBeritaAcara(HttpSession session) {
        String storeCode = (String) session.getAttribute("storeCode");
        if (storeCode == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ClassPathResource resource = new ClassPathResource("data/FORMAT BERITA ACARA PEMUSNAHAN BARANG.xlsx");
        try (InputStream is = resource.getInputStream()) {
            byte[] fileBytes = is.readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "Template_Berita_Acara_Pemusnahan.xlsx");
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // APPROVE & REJECT DENGAN REASON
    @GetMapping("/approve/{id}")
    public String approveLaporan(@PathVariable("id") Long id, HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";
        Laporan laporan = laporanRepository.findById(id).orElse(null);
        if (laporan != null) {
            laporan.setStatusApprove("Approved");
            laporan.setRejectReason(null);
            laporanRepository.save(laporan);
        }
        return "redirect:/dashboard-am";
    }

    @PostMapping("/reject/{id}")
    public String rejectLaporan(@PathVariable("id") Long id,
                                @RequestParam("reason") String reason,
                                HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";
        Laporan laporan = laporanRepository.findById(id).orElse(null);
        if (laporan != null) {
            laporan.setStatusApprove("Rejected");
            laporan.setRejectReason(reason != null ? reason.trim() : "-");
            laporanRepository.save(laporan);
        }
        return "redirect:/dashboard-am";
    }

    // HALAMAN FORM & SUMMARY TOKO
    @GetMapping("/form-laporan")
    public String showFormPage(HttpSession session, Model model) {
        if (session.getAttribute("storeCode") == null) return "redirect:/";
        model.addAttribute("storeCode", session.getAttribute("storeCode"));
        return "form-input";
    }

    @GetMapping("/summary-laporan")
    public String showSummaryPage(@RequestParam(value = "startDate", required = false) String startDate,
                                  @RequestParam(value = "endDate", required = false) String endDate,
                                  HttpSession session, Model model) {
        String storeCode = (String) session.getAttribute("storeCode");
        if (storeCode == null) return "redirect:/";

        List<Laporan> laporanToko = new ArrayList<>();
        for (Laporan l : laporanRepository.findAll()) {
            if (storeCode.equals(l.getStoreCode()) &&
                isMatchDateFilter(l.getTanggalInput(), "semua", startDate, endDate)) {
                laporanToko.add(l);
            }
        }
        model.addAttribute("storeCode", storeCode);
        model.addAttribute("listLaporanToko", laporanToko);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "summary-laporan";
    }

    @GetMapping("/api/cek-plu")
    @ResponseBody
    public Map<String, Object> cekPluAPI(@RequestParam("plu") String plu, HttpSession session) {
        return excelReaderService.getDetailBarang((String) session.getAttribute("storeCode"), plu);
    }

    // ==========================================
    // SUBMIT LAPORAN - OTOMATIS SIMPAN TITLE/ARTICLE DARI HASIL SCAN BARCODE
    // ==========================================
    @PostMapping("/submit-laporan")
    public String submitLaporan(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam("plu") String plu,
            @RequestParam("namaBarang") String namaBarang,
            @RequestParam(value = "expiredDate", required = false) String expiredDate,
            @RequestParam(value = "ldc", defaultValue = "0") String ldc,
            @RequestParam(value = "harga", defaultValue = "0") String harga,
            @RequestParam("keterangan") String keterangan,
            @RequestParam(value = "layakJual", required = false, defaultValue = "-") String layakJual,
            @RequestParam("jenisAjuan") String jenisAjuan,
            @RequestParam("namaPengaju") String namaPengaju,
            HttpSession session) {

        String storeCode = (String) session.getAttribute("storeCode");
        if (storeCode == null) return "redirect:/";

        try {
            String publicUrl = "";
            if (foto != null && !foto.isEmpty()) {
                publicUrl = supabaseStorageService.uploadFile(foto);
            }

            // Cari detail produk berdasarkan input (bisa berupa Barcode atau Article/Title)
            Map<String, Object> detailMaster = excelReaderService.getDetailBarang(storeCode, plu);

            String resolvedTitle = (detailMaster.get("title") != null && !detailMaster.get("title").toString().isEmpty())
                    ? detailMaster.get("title").toString()
                    : plu.trim();

            double hargaAngka = detailMaster.get("harga") instanceof Number
                    ? ((Number) detailMaster.get("harga")).doubleValue() : 0.0;
            double ldcAngka = detailMaster.get("ldc") instanceof Number
                    ? ((Number) detailMaster.get("ldc")).doubleValue() : 0.0;

            Laporan lap = new Laporan();
            lap.setStoreCode(storeCode);
            lap.setPlu(resolvedTitle); // Simpan Title/Article 7 digit ke kolom PLU
            lap.setNamaBarang(namaBarang);
            lap.setExpiredDate(expiredDate);
            lap.setLdc(ldcAngka);
            lap.setHarga(hargaAngka);
            lap.setKeterangan(keterangan);
            lap.setLayakJual(layakJual);
            lap.setJenisAjuan(jenisAjuan);
            lap.setNamaPengaju(namaPengaju);
            lap.setFotoPath(publicUrl != null ? publicUrl : "");

            laporanRepository.save(lap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/form-laporan";
    }
}
