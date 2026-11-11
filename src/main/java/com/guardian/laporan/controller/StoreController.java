package com.guardian.laporan.controller;

import com.guardian.laporan.repository.LaporanRepository;
import com.guardian.laporan.repository.TokoRepository;
import com.guardian.laporan.service.ExcelReaderService;
import com.guardian.laporan.service.SupabaseStorageService;
import com.guardian.laporan.model.Laporan;
import com.guardian.laporan.model.Toko;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
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
    public String showLoginPage() { return "login"; }

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

    // ==========================================
    // DASHBOARD AM - UPDATE REKAP LDC
    // ==========================================
    @GetMapping("/dashboard-am")
    public String dashboardAM(@RequestParam(value = "filter", defaultValue = "semua") String filter,
                              HttpSession session, Model model) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";

        List<Laporan> semuaLaporan = laporanRepository.findAll();
        List<Laporan> laporanFiltered = new ArrayList<>();

        LocalDate now = LocalDate.now();
        for (Laporan l : semuaLaporan) {
            if (l.getTanggalInput() == null) continue;
            boolean masuk = true;
            if (filter.equalsIgnoreCase("mingguan")) {
                if (l.getTanggalInput().isBefore(now.minusDays(7))) masuk = false;
            } else if (filter.equalsIgnoreCase("bulanan")) {
                if (l.getTanggalInput().isBefore(now.minusDays(30))) masuk = false;
            }
            if (masuk) laporanFiltered.add(l);
        }

        double totalWO = 0, totalMarkdown = 0;
        double totalLdcWO = 0, totalLdcMarkdown = 0; // Tambahan rekap LDC

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
    // EXPORT EXCEL - TAMBAHAN LDC DAN EXPIRED
    // ==========================================
    @GetMapping("/am/export-excel")
    public void exportToExcel(@RequestParam(value = "filter", defaultValue = "semua") String filter,
                              HttpServletResponse response, HttpSession session) {
        try {
            if (!"AM".equals(session.getAttribute("role"))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=Laporan_Barang_Rusak_" + filter + ".xlsx");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Rekap Laporan");

            Map<String, String> mapNamaToko = new HashMap<>();
            for (Toko t : tokoRepository.findAll()) {
                mapNamaToko.put(t.getStoreCode(), t.getStoreName());
            }

            Row header = sheet.createRow(0);
            // Menambahkan kolom ED dan LDC
            String[] columns = {"Tanggal", "Kode Toko", "Nama Toko", "PLU", "Nama Barang", "Expired Date", "Keterangan Rusak", "Jenis Ajuan", "LDC (Beli)", "Harga (Jual)", "Pengaju", "Status"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            LocalDate now = LocalDate.now();
            List<Laporan> list = laporanRepository.findAll();
            int rowNum = 1;

            for (Laporan lap : list) {
                if (lap.getTanggalInput() == null) continue;
                boolean masuk = true;
                if (filter.equalsIgnoreCase("mingguan")) {
                    if (lap.getTanggalInput().isBefore(now.minusDays(7))) masuk = false;
                } else if (filter.equalsIgnoreCase("bulanan")) {
                    if (lap.getTanggalInput().isBefore(now.minusDays(30))) masuk = false;
                }
                if (!masuk) continue;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(lap.getTanggalInput() != null ? lap.getTanggalInput().toString() : "-");
                row.createCell(1).setCellValue(lap.getStoreCode() != null ? lap.getStoreCode() : "-");
                row.createCell(2).setCellValue(mapNamaToko.getOrDefault(lap.getStoreCode(), "Toko Tidak Dikenal"));
                row.createCell(3).setCellValue(lap.getPlu() != null ? lap.getPlu() : "-");
                row.createCell(4).setCellValue(lap.getNamaBarang() != null ? lap.getNamaBarang() : "-");
                row.createCell(5).setCellValue(lap.getExpiredDate() != null ? lap.getExpiredDate() : "-");
                row.createCell(6).setCellValue(lap.getKeterangan() != null ? lap.getKeterangan() : "-");
                row.createCell(7).setCellValue(lap.getJenisAjuan() != null ? lap.getJenisAjuan() : "-");
                row.createCell(8).setCellValue(lap.getLdc() != null ? lap.getLdc() : 0);
                row.createCell(9).setCellValue(lap.getHarga() != null ? lap.getHarga() : 0);
                row.createCell(10).setCellValue(lap.getNamaPengaju() != null ? lap.getNamaPengaju() : "-");
                row.createCell(11).setCellValue(lap.getStatusApprove() != null ? lap.getStatusApprove() : "-");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(response.getOutputStream());
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/approve/{id}")
    public String approveLaporan(@PathVariable("id") Long id, HttpSession session) {
        if (!"AM".equals(session.getAttribute("role"))) return "redirect:/";
        Laporan laporan = laporanRepository.findById(id).orElse(null);
        if (laporan != null) {
            laporan.setStatusApprove("Approved");
            laporanRepository.save(laporan);
        }
        return "redirect:/dashboard-am";
    }

    @GetMapping("/form-laporan")
    public String showFormPage(HttpSession session, Model model) {
        if (session.getAttribute("storeCode") == null) return "redirect:/";
        model.addAttribute("storeCode", session.getAttribute("storeCode"));
        return "form-input";
    }

    @GetMapping("/summary-laporan")
    public String showSummaryPage(HttpSession session, Model model) {
        String storeCode = (String) session.getAttribute("storeCode");
        if (storeCode == null) return "redirect:/";

        List<Laporan> laporanToko = new ArrayList<>();
        for (Laporan l : laporanRepository.findAll()) {
            if (storeCode.equals(l.getStoreCode())) laporanToko.add(l);
        }
        model.addAttribute("storeCode", storeCode);
        model.addAttribute("listLaporanToko", laporanToko);
        return "summary-laporan";
    }

    @GetMapping("/api/cek-plu")
    @ResponseBody
    public Map<String, Object> cekPluAPI(@RequestParam("plu") String plu, HttpSession session) {
        return excelReaderService.getDetailBarang((String) session.getAttribute("storeCode"), plu);
    }

    // ==========================================
    // SUBMIT LAPORAN - MENERIMA LDC & ED
    // ==========================================
    @PostMapping("/submit-laporan")
    public String submitLaporan(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam("plu") String plu,
            @RequestParam("namaBarang") String namaBarang,
            @RequestParam(value = "expiredDate", required = false) String expiredDate,
            @RequestParam(value = "ldc", defaultValue = "0") String ldc,
            @RequestParam("harga") String harga,
            @RequestParam("keterangan") String keterangan,
            @RequestParam("layakJual") String layakJual,
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

            // Pembersihan Harga Jual
            String cleanHarga = harga.replaceAll("[^0-9]", "");
            double hargaAngka = cleanHarga.isEmpty() ? 0.0 : Double.parseDouble(cleanHarga);
            if (harga.contains(",00") || harga.contains(".00")) hargaAngka /= 100;

            // Pembersihan LDC
            String cleanLdc = ldc.replaceAll("[^0-9]", "");
            double ldcAngka = cleanLdc.isEmpty() ? 0.0 : Double.parseDouble(cleanLdc);
            if (ldc.contains(",00") || ldc.contains(".00")) ldcAngka /= 100;

            Laporan lap = new Laporan();
            lap.setStoreCode(storeCode);
            lap.setPlu(plu);
            lap.setNamaBarang(namaBarang);
            lap.setExpiredDate(expiredDate); // Simpan ED
            lap.setLdc(ldcAngka);            // Simpan LDC
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