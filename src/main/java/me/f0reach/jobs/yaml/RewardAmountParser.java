package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.RewardAmount;

import java.util.Map;

/**
 * "reward: 5" と "reward: {min:3, max:8}" を吸収する。
 */
public final class RewardAmountParser {

    public RewardAmount parse(Object raw, String path) {
        if (raw == null) {
            throw new YamlParseException("Missing reward at " + path);
        }
        if (raw instanceof Number n) {
            return new RewardAmount.Fixed(n.doubleValue());
        }
        if (raw instanceof Map<?, ?> map) {
            Object min = map.get("min");
            Object max = map.get("max");
            if (!(min instanceof Number) || !(max instanceof Number)) {
                throw new YamlParseException(path + ": reward map requires numeric min/max");
            }
            return new RewardAmount.Range(((Number) min).doubleValue(), ((Number) max).doubleValue());
        }
        throw new YamlParseException(path + ": reward must be a number or {min,max} map");
    }
}
