package com.guardian.laporan.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExcelReaderService {

    private final String BARCODE_FILE = "data/Barcode.xlsx";
    private final String PRICE_ZONE_FILE = "data/FILE PRICE ZONE.xlsx";
    private final String MASTER_PRODUK_FILE = "data/Master Nama Produk.xlsx";
    private final String LDC_FILE = "data/Data LDC.xlsx";

    // Cache memori untuk Barcode.xlsx agar proses scan instan
    private Map<String, ProductInfo> mapByBarcode = null;
    private Map<String, ProductInfo> mapByTitle = null;

    private static class ProductInfo {
        String title;
        String description;
        String barcode;

        ProductInfo(String title, String description, String barcode) {
            this.title = title;
            this.description = description;
            this.barcode = barcode;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    // Membaca angka panjang (seperti Barcode 13 digit) tanpa berubah menjadi format ilmiah (E+12)
    private String getExactNumericOrString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue()).trim();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return String.valueOf((long) cell.getNumericCellValue()).trim();
            } catch (Exception e) {
                return cell.getStringCellValue().replaceAll("[^0-9]", "").trim();
            }
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).replaceAll("[^0-9]", "").trim();
    }

    private InputStream getFileStream(String path) throws Exception {
        return new ClassPathResource(path).getInputStream();
    }

    // Memuat data dari Barcode.xlsx (Kolom A: Title, Kolom B: Description, Kolom C: Barcode)
    private synchronized void ensureBarcodeLoaded() {
        if (mapByBarcode != null && mapByTitle != null) return;

        Map<String, ProductInfo> tempBarcode = new HashMap<>();
        Map<String, ProductInfo> tempTitle = new HashMap<>();

        try (InputStream fis = getFileStream(BARCODE_FILE);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Lewati header

                String title = getExactNumericOrString(row.getCell(0));       // Kolom A: Title (Article/PLU)
                String description = getCellValueAsString(row.getCell(1));    // Kolom B: Description (Nama Barang)
                String barcode = getExactNumericOrString(row.getCell(2));     // Kolom C: Barcode

                if (!title.isEmpty()) {
                    ProductInfo info = new ProductInfo(title, description, barcode);
                    tempTitle.putIfAbsent(title, info);
                    if (!barcode.isEmpty()) {
                        tempBarcode.put(barcode, info);
                    }
                }
            }
            System.out.println("Berhasil memuat Barcode.xlsx: " + tempBarcode.size() + " barcode, " + tempTitle.size() + " article.");
        } catch (Exception e) {
            System.out.println("Gagal baca Barcode.xlsx: " + e.getMessage());
        }

        this.mapByBarcode = tempBarcode;
        this.mapByTitle = tempTitle;
    }

    // 1. Mencari Price Zone dari Kode Toko
    public String getPriceZoneByStore(String storeCode) {
        if (storeCode == null) return "";
        String cleanCode = storeCode.replaceFirst("^0+(?!$)", "").trim();

        try (InputStream fis = getFileStream(PRICE_ZONE_FILE);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(1);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell cellStoreCode = row.getCell(0);
                if (cellStoreCode != null) {
                    String rowCode = getCellValueAsString(cellStoreCode).replaceFirst("^0+(?!$)", "");
                    if (rowCode.equals(cleanCode)) {
                        Cell cellZone = row.getCell(2);
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

    // 2. Mengambil Title, Nama Barang, RSP (Harga Jual), dan Harga LDC dari input Barcode ATAU Article/Title
    public Map<String, Object> getDetailBarang(String storeCode, String inputKode) {
        Map<String, Object> result = new HashMap<>();
        result.put("found", false);
        result.put("title", "");
        result.put("barcode", "");
        result.put("namaBarang", "Produk / PLU Tidak Ditemukan");
        result.put("harga", 0.0);
        result.put("ldc", 0.0);

        if (inputKode == null) return result;
        String cleanInput = inputKode.replaceAll("[^0-9]", "").trim();
        if (cleanInput.isEmpty()) return result;

        ensureBarcodeLoaded();

        // TAHAP A: Cari Title & Nama Barang dari Barcode.xlsx (bisa input Barcode Kolom C atau Title Kolom A)
        String resolvedTitle = "";
        ProductInfo product = mapByBarcode.get(cleanInput);
        if (product == null) {
            product = mapByTitle.get(cleanInput);
        }

        if (product != null) {
            resolvedTitle = product.title;
            result.put("found", true);
            result.put("title", product.title);
            result.put("barcode", product.barcode);
            result.put("namaBarang", product.description);
        } else {
            // Fallback ke Master Nama Produk.xlsx jika Article tidak terdaftar di Barcode.xlsx
            try (InputStream fisMaster = getFileStream(MASTER_PRODUK_FILE);
                 Workbook workbookMaster = WorkbookFactory.create(fisMaster)) {
                Sheet sheetMaster = workbookMaster.getSheetAt(0);
                for (Row row : sheetMaster) {
                    if (row.getRowNum() == 0) continue;
                    String excelPlu = getExactNumericOrString(row.getCell(0));
                    if (excelPlu.equals(cleanInput)) {
                        resolvedTitle = excelPlu;
                        Cell cellNama = row.getCell(1);
                        result.put("found", true);
                        result.put("title", excelPlu);
                        if (cellNama != null) {
                            result.put("namaBarang", getCellValueAsString(cellNama));
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Gagal baca Master Produk: " + e.getMessage());
            }
        }

        // Jika tidak ditemukan sama sekali, hentikan pencarian RSP & LDC
        if (resolvedTitle.isEmpty()) {
            return result;
        }

        // TAHAP B: Cari RSP (Harga Jual) menggunakan Title & Price Zone toko
        String priceZone = getPriceZoneByStore(storeCode);
        try (InputStream fisPrice = getFileStream(PRICE_ZONE_FILE);
             Workbook workbookPrice = WorkbookFactory.create(fisPrice)) {

            Sheet sheetPrice = workbookPrice.getSheetAt(0);
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
                String excelPlu = getExactNumericOrString(row.getCell(0));

                if (excelPlu.equals(resolvedTitle)) {
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
        } catch (Exception e) {
            System.out.println("Gagal baca Price Zone Harga: " + e.getMessage());
        }

        // TAHAP C: Cari Harga LDC (Beli) menggunakan Title
        try (InputStream fisLdc = getFileStream(LDC_FILE);
             Workbook workbookLdc = WorkbookFactory.create(fisLdc)) {

            Sheet sheetLdc = workbookLdc.getSheetAt(0);
            for (Row row : sheetLdc) {
                if (row.getRowNum() == 0) continue;
                String excelPlu = getExactNumericOrString(row.getCell(0));

                if (excelPlu.equals(resolvedTitle)) {
                    Cell cellLdcValue = row.getCell(2);
                    if (cellLdcValue != null) {
                        if (cellLdcValue.getCellType() == CellType.NUMERIC) {
                            result.put("ldc", cellLdcValue.getNumericCellValue());
                        } else {
                            String ldcStr = getCellValueAsString(cellLdcValue).replaceAll("[^0-9]", "");
                            if (!ldcStr.isEmpty()) {
                                result.put("ldc", Double.parseDouble(ldcStr));
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Gagal baca data LDC: " + e.getMessage());
        }

        return result;
    }
}
