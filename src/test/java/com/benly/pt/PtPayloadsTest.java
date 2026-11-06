package com.benly.pt;

import static org.junit.jupiter.api.Assertions.*;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.service.PtPayloads;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PtPayloadsTest {
    @Test
    void rejectsEmptyUnsupportedAndOversizedAudioBeforeReading() {
        assertEquals(
                PtErrorCode.INVALID_REQUEST,
                assertThrows(
                        BusinessException.class,
                        () ->
                                PtPayloads.audioHash(
                                        new MockMultipartFile("audio", new byte[0]),
                                        30,
                                        true))
                        .getErrorCode());
        assertEquals(
                PtErrorCode.UNSUPPORTED_AUDIO,
                assertThrows(
                        BusinessException.class,
                        () ->
                                PtPayloads.audioHash(
                                        new MockMultipartFile(
                                                "audio",
                                                "a.exe",
                                                "audio/webm",
                                                new byte[] {1}),
                                        30,
                                        true))
                        .getErrorCode());
        var oversized =
                new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[] {1}) {
                    @Override
                    public long getSize() {
                        return PtPayloads.MAX_AUDIO_BYTES + 1;
                    }
                };
        assertEquals(
                PtErrorCode.AUDIO_TOO_LARGE,
                assertThrows(
                        BusinessException.class,
                        () -> PtPayloads.audioHash(oversized, 30, true))
                        .getErrorCode());
    }

    @Test
    void presentationDurationIsBoundedAndHashesBindFileAndDuration() {
        var one = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[] {1});
        var renamed = new MockMultipartFile("audio", "renamed.webm", "audio/webm", new byte[] {1});
        var two = new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[] {2});
        assertEquals(
                PtErrorCode.INVALID_REQUEST,
                assertThrows(BusinessException.class, () -> PtPayloads.audioHash(one, 301, true))
                        .getErrorCode());
        assertEquals(PtPayloads.audioHash(one, 30, true), PtPayloads.audioHash(renamed, 30, true));
        assertNotEquals(PtPayloads.audioHash(one, 30, true), PtPayloads.audioHash(two, 30, true));
        assertNotEquals(PtPayloads.audioHash(one, 30, true), PtPayloads.audioHash(one, 31, true));
    }
}