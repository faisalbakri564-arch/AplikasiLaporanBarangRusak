package com.guardian.laporan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final String BUCKET_NAME = "uploads";

    public String uploadFile(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return null;
            }

            // Sanitasi ketat: hanya izinkan huruf, angka, titik, underscore, dan strip
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "foto.jpg";
            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = System.currentTimeMillis() + "_" + safeName;

            String url = supabaseUrl + "/storage/v1/object/" + BUCKET_NAME + "/" + fileName;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("apikey", supabaseKey);
            headers.set("x-upsert", "true");

            // Gunakan MIME type gambar asli agar browser selalu bisa merender foto
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                headers.setContentType(MediaType.parseMediaType(contentType));
            } else {
                headers.setContentType(MediaType.IMAGE_JPEG);
            }

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                return supabaseUrl + "/storage/v1/object/public/" + BUCKET_NAME + "/" + fileName;
            }
        } catch (Exception e) {
            System.err.println("GAGAL UPLOAD KE SUPABASE STORAGE: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
