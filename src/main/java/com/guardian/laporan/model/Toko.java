package com.guardian.laporan.model;

import jakarta.persistence.*;

@Entity
public class Toko {

    @Id
    private String storeCode; // Kode toko otomatis jadi kunci otorisasi
    private String storeName;
    private String email;

    // --- GETTER & SETTER ---
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}