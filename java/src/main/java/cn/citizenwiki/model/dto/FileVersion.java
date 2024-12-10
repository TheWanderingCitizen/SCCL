package cn.citizenwiki.model.dto;

import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import cn.citizenwiki.utils.ParatranzFileUtil;

import java.util.Arrays;
import java.util.Objects;

/**
 * 3.24.4 PTU 98767232
 */
public class FileVersion implements Comparable<FileVersion> {

    private int first; //3
    private int middle; //24
    private int last; //4
    private long version;// 98767232
    private long id;
    //PTU PU还是其它
    private Profile profile;// PTU
    private String name; // 3.24.4 PTU 98767232

    public FileVersion(PZFile pzFile) {
        String realFileName = ParatranzFileUtil.getRealFileName(pzFile.getName());
        if (ParatranzFileUtil.isFormatedName(realFileName)) {
            String[] nameArray = realFileName.split(" ");
            String scVersion = nameArray[0];
            String[] scVersionArray = scVersion.split("\\.");
            String scProfile = nameArray[1];
            String subVersion = substringUntilFirstNonDigit(nameArray[2]);
            this.first = Integer.parseInt(scVersionArray[0]);
            this.middle = Integer.parseInt(scVersionArray[1]);
            if (scVersionArray.length > 2) {
                this.last = Integer.parseInt(scVersionArray[2]);
            }
            this.version = Long.parseLong(subVersion);
            try {
                this.profile = Profile.valueOf(scProfile.strip().toUpperCase());
            }catch (Exception e) {
                //如果不合法，则默认为最低版本
                this.profile = Profile.values()[0];
            }
            this.id = pzFile.getId();
            this.name = realFileName.substring(0, realFileName.lastIndexOf("."));
        }
    }

    /**
     * 枚举顺序代表版本新旧顺序，不可随意变换顺序，越往下越新
     */
    public enum Profile {
        EPTU,
        PTU,
        LIVE,
    }

    @Override
    public int compareTo(FileVersion o) {
        // 比较第一部分
        int firstComparison = Integer.compare(this.first, o.first);
        if (firstComparison != 0) {
            return firstComparison;
        }

        // 比较中间部分
        int middleComparison = Integer.compare(this.middle, o.middle);
        if (middleComparison != 0) {
            return middleComparison;
        }

        // 比较最后部分
        int lastComparison = Integer.compare(this.last, o.last);
        if (lastComparison != 0) {
            return lastComparison;
        }

        //比较PROFILE
        int lastProfile = Integer.compare(this.profile.ordinal(), o.profile.ordinal());
        if (lastProfile != 0) {
            return lastProfile;
        }

        // 如果前三部分相同，比较版本号
        int versionComparison = Long.compare(this.version, o.version);
        if (versionComparison != 0) {
            return versionComparison;
        }

        // 如果版本号也相同，比较ID
        return Long.compare(this.id, o.id);
    }

    /**
     * 将字符串截取至第一个非数字字符
     *
     * @param input
     * @return
     */
    public static String substringUntilFirstNonDigit(String input) {
        int index = 0;

        // 查找第一个不是数字的字符
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }

        // 返回截取的字符串
        return input.length() - 1 == index ? input : input.substring(0, index);
    }

    public int getFirst() {
        return first;
    }

    public int getMiddle() {
        return middle;
    }

    public int getLast() {
        return last;
    }

    public long getVersion() {
        return version;
    }

    public long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getName() {
        return name;
    }
}
