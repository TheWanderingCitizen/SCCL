package cn.citizenwiki.processor;

/**
 * 基础处理器接口
 */
public interface BaseProcessor {

    /**
     * 获取处理器的名称，用于日志等场景
     * @return
     */
    String getProcessorName();

}
