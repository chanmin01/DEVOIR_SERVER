package com.benly.pt;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.service.PtPayloads;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class PtPayloadsTest {

    @Test
    void rejectsEmptyUnsupportedAndOversizedAudioBeforeReading() {
        assertEquals(
                PtErrorCode.INVALID_REQUEST,
                assertThrows(
                        BusinessException.class,
                        () -> PtPayloads.audioHash(
                                new MockMultipartFile(
                                        "audio",
                                        new byte[0]
                                ),
                                30
                        )
                ).getErrorCode()
        );

        assertEquals(
                PtErrorCode.UNSUPPORTED_AUDIO,
                assertThrows(
                        BusinessException.class,
                        () -> PtPayloads.audioHash(
                                new MockMultipartFile(
                                        "audio",
                                        "a.exe",
                                        "audio/webm",
                                        new byte[]{1}
                                ),
                                30
                        )
                ).getErrorCode()
        );

        var oversized = new MockMultipartFile(
                "audio",
                "a.webm",
                "audio/webm",
                new byte[]{1}
        ) {
            @Override
            public long getSize() {
                return PtPayloads.MAX_AUDIO_BYTES + 1;
            }
        };

        assertEquals(
                PtErrorCode.AUDIO_TOO_LARGE,
                assertThrows(
                        BusinessException.class,
                        () -> PtPayloads.audioHash(
                                oversized,
                                30
                        )
                ).getErrorCode()
        );
    }

    @Test
    void validatesPositiveDurationAndHashesBindFileAndDuration() {
        var one = new MockMultipartFile(
                "audio",
                "a.webm",
                "audio/webm",
                new byte[]{1}
        );

        var renamed = new MockMultipartFile(
                "audio",
                "renamed.webm",
                "audio/webm",
                new byte[]{1}
        );

        var two = new MockMultipartFile(
                "audio",
                "a.webm",
                "audio/webm",
                new byte[]{2}
        );

        assertThrows(
                BusinessException.class,
                () -> PtPayloads.audioHash(one, null)
        );

        assertThrows(
                BusinessException.class,
                () -> PtPayloads.audioHash(one, 0)
        );

        assertThrows(
                BusinessException.class,
                () -> PtPayloads.audioHash(one, -1)
        );

        // 과제별 상한은 트랜잭션에서 검사한다.
        assertDoesNotThrow(
                () -> PtPayloads.audioHash(one, 600)
        );

        assertEquals(
                PtPayloads.audioHash(one, 30),
                PtPayloads.audioHash(renamed, 30)
        );

        assertNotEquals(
                PtPayloads.audioHash(one, 30),
                PtPayloads.audioHash(two, 30)
        );

        assertNotEquals(
                PtPayloads.audioHash(one, 30),
                PtPayloads.audioHash(one, 31)
        );
    }
}