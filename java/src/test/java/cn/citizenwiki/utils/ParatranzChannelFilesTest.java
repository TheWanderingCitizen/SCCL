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

    @Test
    void inheritsHistoricalBaseFilesAndLegacyNamesSeenInThePublishedRun() {
        PZFile live = file("4.10.3 LIVE 13165946.json", 1);
        List<PZFile> history = List.of(
                file("3.24.X/3.24.0全文_0.json", 2),
                file("3.24.X/3.24.0全文_1.json", 3),
                file("3.24.X/3.24.0 Live-9296942新增-改变 (2).json", 4),
                file("3.24.X/3.24.1 Live-9324446 新增.json", 5),
                file("3.24.X/3.24.1 PTU-9315193 新增-改变", 6),
                file("3.24.X/3.24.3 PTU 9412741", 7),
                file("4.0-4.6/4.0 EPTU 9418963-0.json", 8),
                file("4.0-4.6/4.0.1 PTU 9493308-2.json", 9),
                file("4.0-4.6/4.1.1 PTU.9721902.json", 10),
                file("4.0-4.6/4.2.1 PTU  9900767.json", 11),
                file("4.0-4.6/4.3.1 PTU 10262876", 12),
                file("4.8.X/4.8.2 LIVE.json", 13),
                file("4.10.X/4.10.2 TECH-PREVIEW 12702948.json", 14));
        var files = new java.util.ArrayList<>(history);
        files.add(live);
        assertEquals(files, ParatranzChannelFiles.select(files, Profile.LIVE).files());
    }

    @Test
    void legacySuffixesAndUnknownBuildsCannotBypassTheChannelCutoff() {
        PZFile live = file("4.10.3 LIVE 13165946.json", 1);
        PZFile older = file("4.10.3 PTU-13160301新增.json", 2);
        var selection = ParatranzChannelFiles.select(List.of(live, older,
                file("4.10.3 PTU-13170000新增.json", 3),
                file("5.0全文_0.json", 4), file("4.10.3全文_0.json", 5),
                file("4.10.3 PTU.json", 6), file("4.10.3 LIVE.json", 7)), Profile.LIVE);
        assertEquals(List.of(live, older), selection.files());
    }

    private static PZFile file(String name, long id) {
        PZFile file = new PZFile();
        file.setName(name);
        file.setId(id);
        return file;
    }
}
