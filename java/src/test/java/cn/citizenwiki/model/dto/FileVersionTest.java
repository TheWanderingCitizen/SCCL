package cn.citizenwiki.model.dto;

import cn.citizenwiki.model.dto.paratranz.response.PZFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileVersionTest {

    // 测试比较第一部分 (first)
    @Test
    void testCompareFirstPart() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("4.24.4 PTU 98767233"));

        // fv1的first是3，fv2的first是4，3 < 4
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 测试比较中间部分 (middle)
    @Test
    void testCompareMiddlePart() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("3.25.4 PTU 98767233"));

        // fv1的middle是24，fv2的middle是25，24 < 25
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 测试比较最后部分 (last)
    @Test
    void testCompareLastPart() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("3.24.5 PTU 98767233"));

        // fv1的last是4，fv2的last是5，4 < 5
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 测试比较profile (PTU vs LIVE)
    @Test
    void testCompareProfile() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("3.24.4 LIVE 98767233"));

        // fv1的profile是PTU，fv2的profile是LIVE，PTU < LIVE
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 测试比较版本号 (version)
    @Test
    void testCompareVersion() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("3.24.4 PTU 98767233"));

        // fv1的version是98767232，fv2的version是98767233，98767232 < 98767233
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 测试比较ID (id)
    @Test
    void testCompareId() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232", 1L));
        FileVersion fv2 = new FileVersion(createPZFile("3.24.4 PTU 98767232", 2L));


        // 比较ID，fv1的ID小，fv2的ID大
        assertTrue(fv1.compareTo(fv2) < 0);
        assertTrue(fv2.compareTo(fv1) > 0);
    }

    // 边界情况：两个完全相同的FileVersion
    @Test
    void testCompareEqualFileVersions() {
        FileVersion fv1 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));
        FileVersion fv2 = new FileVersion(createPZFile("3.24.4 PTU 98767232"));

        // 两个FileVersion完全相同，应该返回0
        assertEquals(0, fv1.compareTo(fv2));
    }

    // 辅助方法：根据文件名创建PZFile对象
    private PZFile createPZFile(String name) {
        return createPZFile(name, 0L);
    }

    // 重载createPZFile方法，支持传递ID
    private PZFile createPZFile(String name, long id) {
        PZFile pzFile = new PZFile();
        pzFile.setName(name + ".json");  // 设置文件名
        pzFile.setId(id);      // 设置ID
        return pzFile;
    }
}
