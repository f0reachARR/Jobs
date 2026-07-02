package me.f0reach.jobs.yaml;

/**
 * 1 エントリの YAML パース中の詰まった箇所を上位に伝える runtime 例外。
 * ローダ側で catch して YamlErrors にエントリとして積む。
 */
public class YamlParseException extends RuntimeException {
    public YamlParseException(String message) {
        super(message);
    }

    public YamlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
