package cn.citizenwiki.utils;

import cn.citizenwiki.model.dto.FileVersion.Profile;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParatranzChannelFilesTest {
    @Test
    void liveInheritsOlderChannelsButRejectsNewerPtuBuildsAndReleases() {
        PZFile live = file("4.10.0 LIVE 125.json", 1);
        PZFile olderLive = file("4.10.0 LIVE 120.json", 2);
        PZFile olderPtu = file("4.10.0 PTU 124.json", 3);
        PZFile olderRelease = file("4.9.9 PTU 999.json", 4);
        PZFile newerPtu = file("4.10.0 PTU 126.json", 5);
        PZFile newerRelease = file("4.11.0 PTU 1.json", 6);
        PZFile equalPtu = file("4.10.0 PTU 125.json", 7);

        var selection = ParatranzChannelFiles.select(
                List.of(live, olderLive, olderPtu, olderRelease, newerPtu, newerRelease, equalPtu), Profile.LIVE);

        assertEquals("4.10.0 LIVE 125", selection.latestVersion().getName());
        assertEquals(List.of(live, olderLive, olderPtu, olderRelease), selection.files());
    }

    @Test
    void ptuUsesItsOwnLatestBuildAndInheritsEarlierLiveAndEptu() {
        PZFile ptu = file("4.10.0 PTU 130.json", 1);
        PZFile olderLive = file("4.10.0 LIVE 125.json", 2);
        PZFile eptu = file("4.10.0 EPTU 129.json", 3);
        PZFile newerLive = file("4.10.0 LIVE 140.json", 4);
        PZFile equalLive = file("4.10.0 LIVE 130.json", 5);
        PZFile newerRelease = file("4.11.0 LIVE 1.json", 6);

        var selection = ParatranzChannelFiles.select(
                List.of(ptu, olderLive, eptu, newerLive, equalLive, newerRelease), Profile.PTU);

        assertEquals(Profile.PTU, selection.latestVersion().getProfile());
        assertEquals("4.10.0 PTU 130", selection.latestVersion().getName());
        assertEquals(List.of(ptu, olderLive, eptu), selection.files());
    }

    @Test
    void acceptsSubdirectoriesAndKeepsAllSameChannelCopiesAtTheCutoff() {
        PZFile first = file("archive/4.10.0 live 125.json", 1);
        PZFile second = file("current/4.10.0 LIVE 125.json", 2);
        var selection = ParatranzChannelFiles.select(List.of(first, second), Profile.LIVE);
        assertEquals(2, selection.latestVersion().getId());
        assertEquals(List.of(first, second), selection.files());
    }

    @Test
    void ignoresRulesUnknownChannelsAndUnversionedFiles() {
        PZFile live = file("4.10.0 LIVE 125.json", 1);
        PZFile rule = file("汉化规则/9.0.0 LIVE 999.json", 2);
        rule.setFolder("汉化规则");
        var selection = ParatranzChannelFiles.select(List.of(live, rule,
                file("2026-11-15_01-02-03.json", 3), file("global.json", 4),
                file("4.10.0 TEST 124.json", 5), file("oops LIVE bad.json", 6)), Profile.LIVE);
        assertEquals(List.of(live), selection.files());
    }

    @Test
    void stopsWhenTheRequestedChannelHasNoVersionFiles() {
        assertThrows(IllegalStateException.class, () -> ParatranzChannelFiles.select(
                List.of(file("4.10.0 PTU 130.json", 1)), Profile.LIVE));
        assertThrows(IllegalStateException.class, () -> ParatranzChannelFiles.select(List.of(), Profile.PTU));
    }

    private static PZFile file(String name, long id) {
        PZFile file = new PZFile();
        file.setName(name);
        file.setId(id);
        return file;
    }
}
