package cn.citizenwiki.utils;

import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** 按发布通道选择版本上限，并继承严格早于该版本的其他通道文件。 */
public final class ParatranzChannelFiles {
    private static final Pattern VERSION_NAME = Pattern.compile(
            "\\d+\\.\\d+(?:\\.\\d+)? (?:EPTU|PTU|LIVE) \\d+\\.json", Pattern.CASE_INSENSITIVE);
    // 跨通道比较先看版本号，再看构建号；不能使用 FileVersion 的通道优先级。
    private static final Comparator<FileVersion> RELEASE_ORDER = Comparator
            .comparingInt(FileVersion::getFirst)
            .thenComparingInt(FileVersion::getMiddle)
            .thenComparingInt(FileVersion::getLast)
            .thenComparingLong(FileVersion::getVersion);

    private ParatranzChannelFiles() {}

    public record Selection(FileVersion latestVersion, List<PZFile> files) {}

    private record VersionedFile(PZFile file, FileVersion version) {}

    public static Selection select(List<PZFile> files, FileVersion.Profile channel) {
        List<VersionedFile> versionedFiles = files.stream()
                .filter(file -> !"汉化规则".equals(file.getFolder()))
                .filter(file -> file.getName() != null
                        && VERSION_NAME.matcher(ParatranzFileUtil.getRealFileName(file.getName())).matches())
                .map(file -> new VersionedFile(file, new FileVersion(file)))
                .toList();
        FileVersion latest = versionedFiles.stream()
                .map(VersionedFile::version)
                .filter(version -> version.getProfile() == channel)
                .max(RELEASE_ORDER.thenComparingLong(FileVersion::getId))
                .orElseThrow(() -> new IllegalStateException("未找到 " + channel + " 通道的有效版本文件，停止合并"));
        List<PZFile> selected = versionedFiles.stream()
                .filter(file -> {
                    int comparison = RELEASE_ORDER.compare(file.version(), latest);
                    return comparison < 0 || (comparison == 0 && file.version().getProfile() == channel);
                })
                .map(VersionedFile::file)
                .toList();
        return new Selection(latest, selected);
    }
}
