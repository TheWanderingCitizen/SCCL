package cn.citizenwiki;

import cn.citizenwiki.api.paratranz.ParatranzCache;

/**
 * 将Paratranz的数据缓存到本地
 */
public class RestoreParatranzCache {

    public static void main(String[] args) throws Exception {
        ParatranzCache.INSTANCE.restorePatatranzCache();
    }
}
