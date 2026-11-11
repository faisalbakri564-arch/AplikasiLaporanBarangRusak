package com.guardian.laporan.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Laporan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storeCode;
    private String plu;
    private String namaBarang;
    private Double harga;
    private String keterangan;
    private String layakJual;
    private String jenisAjuan; // Write Off atau Markdown
    private String namaPengaju;
    private String fotoPath; // Lokasi file foto disimpan

    // --- TAMBAHAN BARU UNTUK EXPIRED DAN LDC ---
    @Column(name = "expired_date")
    private String expiredDate;

    @Column(name = "ldc")
    private Double ldc;
    // -------------------------------------------

    private String statusApprove = "Pending"; // Status awal
    private LocalDate tanggalInput = LocalDate.now(); // Tanggal hari ini otomatis

    // --- GETTER & SETTER (Wajib ada agar Spring Boot bisa membaca data) ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }

    public String getPlu() { return plu; }
    public void setPlu(String plu) { this.plu = plu; }

    public String getNamaBarang() { return namaBarang; }
    public void setNamaBarang(String namaBarang) { this.namaBarang = namaBarang; }

    public Double getHarga() { return harga; }
    public void setHarga(Double harga) { this.harga = harga; }

    public String getKeterangan() { return keterangan; }
    public void setKeterangan(String keterangan) { this.keterangan = keterangan; }

    public String getLayakJual() { return layakJual; }
    public void setLayakJual(String layakJual) { this.layakJual = layakJual; }

    public String getJenisAjuan() { return jenisAjuan; }
    public void setJenisAjuan(String jenisAjuan) { this.jenisAjuan = jenisAjuan; }

    public String getNamaPengaju() { return namaPengaju; }
    public void setNamaPengaju(String namaPengaju) { this.namaPengaju = namaPengaju; }

    public String getFotoPath() { return fotoPath; }
    public void setFotoPath(String fotoPath) { this.fotoPath = fotoPath; }

    public String getStatusApprove() { return statusApprove; }
    public void setStatusApprove(String statusApprove) { this.statusApprove = statusApprove; }

    public LocalDate getTanggalInput() { return tanggalInput; }
    public void setTanggalInput(LocalDate tanggalInput) { this.tanggalInput = tanggalInput; }

    // --- GETTER & SETTER TAMBAHAN ---
    public String getExpiredDate() { return expiredDate; }
    public void setExpiredDate(String expiredDate) { this.expiredDate = expiredDate; }

    public Double getLdc() { return ldc; }
    public void setLdc(Double ldc) { this.ldc = ldc; }
}