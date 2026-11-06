package com.benly.pt;

import com.benly.pt.repository.PtSessionRepository;
import com.benly.pt.service.PtGuestExpiryJob;
import com.benly.pt.service.PtGuestExpiryTx;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.mockito.Mockito.*;

class PtGuestExpiryJobTest {
    @Test
    void aFailedItemDoesNotPreventFollowingItemsFromExpiring() {
        var details = mock(PtSessionRepository.class);
        var tx = mock(PtGuestExpiryTx.class);
        when(details.findExpiredGuestIds(any(), any())).thenReturn(List.of(1L, 2L, 3L));
        when(tx.expire(1L)).thenThrow(new IllegalStateException("한 건 실패"));
        new PtGuestExpiryJob(details, tx,
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC)).expireGuests();
        verify(tx).expire(1L);
        verify(tx).expire(2L);
        verify(tx).expire(3L);
    }
}