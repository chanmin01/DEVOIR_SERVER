package com.benly.pt.service;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class PtPayloads {
    public static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;
    private static final Set<String> EXTENSIONS =
            Set.of("mp3", "mp4", "m4a", "wav", "webm", "ogg", "mpeg", "mpga");

    private PtPayloads() {}

    public static String textHash(String text) {
        return hash(
                "TEXT\n".getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8));
    }

    public static String audioHash(MultipartFile file, Integer durationSec, boolean presentation) {
        if (file == null
                || file.isEmpty()
                || durationSec == null
                || durationSec < 1
                || (presentation && durationSec > 300)) {
            throw new BusinessException(PtErrorCode.INVALID_REQUEST);
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(PtErrorCode.AUDIO_TOO_LARGE);
        }
        String name = file.getOriginalFilename();
        String extension =
                name == null || !name.contains(".")
                        ? ""
                        : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new BusinessException(PtErrorCode.UNSUPPORTED_AUDIO);
        }
        try {
            return hash(
                    ("AUDIO\n" + durationSec + "\n").getBytes(StandardCharsets.UTF_8),
                    file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(PtErrorCode.INVALID_REQUEST);
        }
    }

    private static String hash(byte[] prefix, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix);
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}