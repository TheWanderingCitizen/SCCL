package cn.citizenwiki.api.paratranz;

import cn.citizenwiki.model.dto.paratranz.response.PZTranslation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParatranzApiTest {
    @Test
    void keepsDraftsAcrossAllPagesAndIgnoresAdditionalStringFields() throws Exception {
        ParatranzApi api = mock(ParatranzApi.class, CALLS_REAL_METHODS);
        doReturn(ParatranzJacksonTools.om.readTree("""
                {"pageCount":2,"results":[{"id":1,"key":"draft","stage":0,
                "translation":"草稿译文","file":42,"updatedAt":"2026-11-15"}]}
                """)).when(api).fileStringsPage(42L, 1);
        doReturn(ParatranzJacksonTools.om.readTree("""
                {"pageCount":2,"results":[{"id":2,"key":"checked","stage":3,"translation":"已检查译文"}]}
                """)).when(api).fileStringsPage(42L, 2);

        List<PZTranslation> entries = api.fileTranslation(42L);
        assertEquals(2, entries.size());
        assertEquals(0, entries.get(0).getStage());
        assertEquals("草稿译文", entries.get(0).getTranslation());
        verify(api).fileStringsPage(42L, 2);
        verify(api, never()).fileStringsPage(42L, 3);
    }

    @Test
    void handlesEmptyFilesAndRejectsIncompleteResponses() throws Exception {
        ParatranzApi api = mock(ParatranzApi.class, CALLS_REAL_METHODS);
        doReturn(ParatranzJacksonTools.om.readTree("{\"pageCount\":0,\"results\":[]}"))
                .when(api).fileStringsPage(42L, 1);
        assertTrue(api.fileTranslation(42L).isEmpty());
        doReturn(ParatranzJacksonTools.om.readTree("{\"pageCount\":2,\"results\":[]}"))
                .when(api).fileStringsPage(42L, 1);
        assertThrows(IllegalStateException.class, () -> api.fileTranslation(42L));
        doReturn(ParatranzJacksonTools.om.readTree("{\"results\":[]}"))
                .when(api).fileStringsPage(42L, 1);
        assertThrows(IllegalStateException.class, () -> api.fileTranslation(42L));
    }
}
