package cn.citizenwiki;

import cn.citizenwiki.model.dto.FileVersion.Profile;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.utils.ParatranzChannelFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelMergeTest {
    @TempDir
    Path cache;

    @Test
    void liveKeepsDraftsFromSelectedHistoryAndDoesNotReadNewerPtuCache() throws Exception {
        PZFile live = file("4.10.0 LIVE 125.json", 1);
        PZFile olderPtu = file("4.10.0 PTU 124.json", 2);
        PZFile newerPtu = file("4.10.0 PTU 126.json", 3);
        Files.writeString(cache.resolve(live.getName()), """
                [{"id":10,"key":"shared","original":"Source","translation":"LIVE 草稿","stage":0},
                 {"id":11,"key":"blank","original":"Blank source","translation":"  ","stage":0}]
                """);
        Files.writeString(cache.resolve(olderPtu.getName()), """
                [{"id":9,"key":"shared","original":"Source","translation":"旧译文","stage":5},
                 {"id":12,"key":"history","original":"History","translation":"历史草稿","stage":0}]
                """);
        // 故意不创建新版 PTU 的缓存文件，确保它不会被读取或参与覆盖。
        var selected = ParatranzChannelFiles.select(List.of(live, olderPtu, newerPtu), Profile.LIVE);
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        original.put("shared", "Source");
        original.put("history", "History");
        original.put("blank", "Blank source");
        original.put("missing", "Missing source");

        var merged = MergeAndConvert.mergeTranslateData(original, selected.files(), cache);

        assertEquals("LIVE 草稿", merged.get("shared").getTranslation());
        assertEquals(0, merged.get("shared").getStage());
        assertEquals("历史草稿", merged.get("history").getTranslation());
        assertEquals("Blank source", merged.get("blank").getTranslation());
        assertEquals("Missing source", merged.get("missing").getTranslation());
        assertEquals(original.keySet(), merged.keySet());
    }

    @Test
    void ptuKeepsDraftsAndRejectsNewerLiveCache() throws Exception {
        PZFile ptu = file("4.10.0 PTU 130.json", 1);
        PZFile olderLive = file("4.10.0 LIVE 125.json", 2);
        PZFile newerLive = file("4.10.0 LIVE 140.json", 3);
        Files.writeString(cache.resolve(ptu.getName()), """
                [{"id":20,"key":"shared","original":"Source","translation":"PTU 草稿","stage":0}]
                """);
        Files.writeString(cache.resolve(olderLive.getName()), """
                [{"id":19,"key":"shared","original":"Source","translation":"旧 LIVE","stage":5}]
                """);
        var selected = ParatranzChannelFiles.select(List.of(ptu, olderLive, newerLive), Profile.PTU);
        var original = new LinkedHashMap<String, String>();
        original.put("shared", "Source");

        var merged = MergeAndConvert.mergeTranslateData(original, selected.files(), cache);

        assertEquals("PTU 草稿", merged.get("shared").getTranslation());
        assertEquals(0, merged.get("shared").getStage());
    }

    private static PZFile file(String name, long id) {
        PZFile file = new PZFile();
        file.setName(name);
        file.setId(id);
        return file;
    }
}
