package com.guardian.laporan.repository;

import com.guardian.laporan.model.Laporan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LaporanRepository extends JpaRepository<Laporan, Long> {
    // Kosong saja, Java yang akan mengurus logika simpan dan bacanya
}