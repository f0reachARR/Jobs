package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.RareBonus;

import java.util.Map;

public final class RareBonusParser {

    private final RewardAmountParser rewardAmountParser;

    public RareBonusParser(RewardAmountParser rewardAmountParser) {
        this.rewardAmountParser = rewardAmountParser;
    }

    public RareBonus parse(Map<?, ?> map, String path) {
        Object chance = map.get("chance");
        if (!(chance instanceof Number n)) {
            throw new YamlParseException(path + ".chance: expected number");
        }
        Object reward = map.get("reward");
        Object announce = map.get("announce");
        return new RareBonus(
                n.doubleValue(),
                rewardAmountParser.parse(reward, path + ".reward"),
                announce == null ? null : announce.toString()
        );
    }
}
