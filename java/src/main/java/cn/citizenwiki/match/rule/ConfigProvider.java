package cn.citizenwiki.match.rule;

/**
 * 配置提供者接口
 * 
 * 用于获取导入的配置文件，支持泛型以适应不同类型的配置对象。
 */
@FunctionalInterface
public interface ConfigProvider<T> {
    /**
     * 根据文件名获取配置对象
     * 
     * @param fileName 配置文件名
     * @return 配置对象，如果文件不存在返回null
     */
    T getConfig(String fileName);
}