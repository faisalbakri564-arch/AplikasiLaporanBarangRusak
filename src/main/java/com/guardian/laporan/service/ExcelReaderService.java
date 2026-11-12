package com.guardian.laporan.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExcelReaderService {

    private final String PRICE_ZONE_FILE = "data/FILE PRICE ZONE.xlsx";
    private final String MASTER_PRODUK_FILE = "data/Master Nama Produk.xlsx";
    private final String LDC_FILE = "data/Data LDC.xlsx"; // Path file LDC

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    // 1. Mencari Price Zone dari Kode Toko
    public String getPriceZoneByStore(String storeCode) {
        if (storeCode == null) return "";
        String cleanCode = storeCode.replaceFirst("^0+(?!$)", "").trim();

        try (FileInputStream fis = new FileInputStream(PRICE_ZONE_FILE);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(1); // Baca Sheet 2
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip baris judul

                Cell cellStoreCode = row.getCell(0); // Kolom A
                if (cellStoreCode != null) {
                    String rowCode = getCellValueAsString(cellStoreCode).replaceFirst("^0+(?!$)", "");
                    if (rowCode.equals(cleanCode)) {
                        Cell cellZone = row.getCell(2); // Kolom C
                        if (cellZone != null) {
                            return getCellValueAsString(cellZone);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Gagal baca Price Zone: " + e.getMessage());
        }
        return "";
    }

    // 2. Mengambil Nama, Harga Jual, dan Harga LDC
    public Map<String, Object> getDetailBarang(String storeCode, String plu) {
        Map<String, Object> result = new HashMap<>();
        result.put("namaBarang", "PLU Tidak Ditemukan");
        result.put("harga", 0.0);
        result.put("ldc", 0.0); // Default nilai LDC

        if (plu == null) return result;
        String cleanPlu = plu.replaceAll("[^0-9]", ""); // Pastikan input PLU murni angka

        // TAHAP A: Baca Nama Barang
        try (FileInputStream fisMaster = new FileInputStream(MASTER_PRODUK_FILE);
             Workbook workbookMaster = WorkbookFactory.create(fisMaster)) {
            Sheet sheetMaster = workbookMaster.getSheetAt(0);
            for (Row row : sheetMaster) {
                if (row.getRowNum() == 0) continue;
                Cell cellPlu = row.getCell(0);

                String excelPlu = getCellValueAsString(cellPlu).replaceAll("[^0-9]", "");
                if (excelPlu.equals(cleanPlu)) {
                    Cell cellNama = row.getCell(1);
                    if (cellNama != null) result.put("namaBarang", getCellValueAsString(cellNama));
                    break;
                }
            }
        } catch (Exception e) {}

        // TAHAP B: Baca Harga Barang (Jual)
        String priceZone = getPriceZoneByStore(storeCode);
        try (FileInputStream fisPrice = new FileInputStream(PRICE_ZONE_FILE);
             Workbook workbookPrice = WorkbookFactory.create(fisPrice)) {

            Sheet sheetPrice = workbookPrice.getSheetAt(0); // Sheet 1
            Row headerRow = sheetPrice.getRow(0);
            int targetColumn = -1;

            if (headerRow != null && !priceZone.isEmpty()) {
                for (Cell cell : headerRow) {
                    if (getCellValueAsString(cell).equalsIgnoreCase(priceZone)) {
                        targetColumn = cell.getColumnIndex();
                        break;
                    }
                }
            }

            if (targetColumn == -1) targetColumn = 1;

            for (Row row : sheetPrice) {
                if (row.getRowNum() == 0) continue;
                Cell cellPlu = row.getCell(0);

                String excelPlu = getCellValueAsString(cellPlu).replaceAll("[^0-9]", "");

                if (excelPlu.equals(cleanPlu)) {
                    Cell priceCell = row.getCell(targetColumn);
                    if (priceCell != null) {
                        try {
                            result.put("harga", priceCell.getNumericCellValue());
                        } catch (Exception e) {
                            String priceStr = getCellValueAsString(priceCell).replaceAll("[^0-9]", "");
                            if (!priceStr.isEmpty()) result.put("harga", Double.parseDouble(priceStr));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {}

        // TAHAP C: Baca Harga LDC (Beli)
        try (FileInputStream fisLdc = new FileInputStream(new File(LDC_FILE));
             Workbook workbookLdc = WorkbookFactory.create(fisLdc)) {

            Sheet sheetLdc = workbookLdc.getSheetAt(0);
            for (Row row : sheetLdc) {
                if (row.getRowNum() == 0) continue; // Skip header
                Cell cellPlu = row.getCell(0); // PLU di Kolom A
                if (cellPlu == null) continue;

                // CARA KETAT: Membaca PLU agar kebal format
                String excelPlu = "";
                if (cellPlu.getCellType() == CellType.NUMERIC) {
                    excelPlu = String.valueOf((long) cellPlu.getNumericCellValue());
                } else {
                    excelPlu = getCellValueAsString(cellPlu).replaceAll("[^0-9]", ""); // Hapus semua selain angka
                }

                if (excelPlu.equals(cleanPlu)) {
                    Cell cellLdcValue = row.getCell(2); // LDC di Kolom C (Index 2)
                    if (cellLdcValue != null) {
                        if (cellLdcValue.getCellType() == CellType.NUMERIC) {
                            result.put("ldc", cellLdcValue.getNumericCellValue());
                        } else {
                            // Membuang titik/koma jika terformat text
                            String ldcStr = getCellValueAsString(cellLdcValue).replaceAll("[^0-9]", "");
                            if (!ldcStr.isEmpty()) {
                                result.put("ldc", Double.parseDouble(ldcStr));
                            }
                        }
                    }
                    break; // Keluar dari loop setelah menemukan PLU
                }
            }
        } catch (Exception e) {
            System.out.println("Gagal baca data LDC: " + e.getMessage());
        }

        return result;
    }
}
