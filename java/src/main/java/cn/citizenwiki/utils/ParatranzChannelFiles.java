package cn.citizenwiki.utils;

import cn.citizenwiki.model.dto.FileVersion;
import cn.citizenwiki.model.dto.paratranz.response.PZFile;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按发布通道选择版本上限，并继承严格早于该版本的其他通道文件。 */
public final class ParatranzChannelFiles {
    private static final Pattern RELEASE_NAME = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(.*)$");
    private static final Pattern CHANNEL_NAME = Pattern.compile(
            "^(EPTU|PTU|LIVE|HOTFIX|TECH-PREVIEW)(?:[\\s.-]+(\\d+)(.*)|(?:\\.json)?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_NAME = Pattern.compile("^全文(?:[_ -]\\d+)?(?:\\.json)?$");
    // 跨通道比较先看版本号，再看构建号；不能使用 FileVersion 的通道优先级。
    private static final Comparator<VersionedFile> RELEASE_ORDER = Comparator
            .comparingInt(VersionedFile::first)
            .thenComparingInt(VersionedFile::middle)
            .thenComparingInt(VersionedFile::last);
    private static final Comparator<VersionedFile> BUILD_ORDER = RELEASE_ORDER
            .thenComparingLong(VersionedFile::build);

    private ParatranzChannelFiles() {}

    public record Selection(FileVersion latestVersion, List<PZFile> files) {}

    private record VersionedFile(PZFile file, int first, int middle, int last,
                                 String releaseName, String channel, Long build) {
        FileVersion fileVersion() {
            PZFile normalized = new PZFile();
            normalized.setName(releaseName + " " + channel + " " + build + ".json");
            normalized.setId(file.getId());
            return new FileVersion(normalized);
        }
    }

    private static Optional<VersionedFile> parse(PZFile file) {
        if ("汉化规则".equals(file.getFolder()) || file.getName() == null) {
            return Optional.empty();
        }
        Matcher release = RELEASE_NAME.matcher(ParatranzFileUtil.getRealFileName(file.getName()));
        if (!release.matches()) {
            return Optional.empty();
        }
        String suffix = release.group(4).strip();
        String channel = null;
        Long build = null;
        if (!BASE_NAME.matcher(suffix).matches()) {
            Matcher channelName = CHANNEL_NAME.matcher(suffix);
            if (!channelName.matches()) {
                return Optional.empty();
            }
            channel = channelName.group(1).toUpperCase(java.util.Locale.ROOT);
            if (channelName.group(2) != null) {
                build = Long.parseLong(channelName.group(2));
            }
        }
        String releaseName = release.group(1) + "." + release.group(2)
                + (release.group(3) == null ? "" : "." + release.group(3));
        return Optional.of(new VersionedFile(file, Integer.parseInt(release.group(1)),
                Integer.parseInt(release.group(2)), release.group(3) == null ? 0 : Integer.parseInt(release.group(3)),
                releaseName, channel, build));
    }

    public static Selection select(List<PZFile> files, FileVersion.Profile channel) {
        List<VersionedFile> versionedFiles = files.stream()
                .map(ParatranzChannelFiles::parse)
                .flatMap(Optional::stream)
                .toList();
        VersionedFile latest = versionedFiles.stream()
                .filter(version -> channel.name().equals(version.channel()) && version.build() != null)
                .max(BUILD_ORDER.thenComparingLong(version -> version.file().getId()))
                .orElseThrow(() -> new IllegalStateException("未找到 " + channel + " 通道的有效版本文件，停止合并"));
        List<PZFile> selected = versionedFiles.stream()
                .filter(file -> {
                    int releaseComparison = RELEASE_ORDER.compare(file, latest);
                    if (releaseComparison != 0) {
                        return releaseComparison < 0;
                    }
                    // 缺少构建号的历史文件只可继承自更早的主版本，不能绕过当前版本的上限。
                    if (file.build() == null) {
                        return false;
                    }
                    int comparison = Long.compare(file.build(), latest.build());
                    return comparison < 0 || (comparison == 0 && channel.name().equals(file.channel()));
                })
                .map(VersionedFile::file)
                .toList();
        return new Selection(latest.fileVersion(), selected);
    }
}
