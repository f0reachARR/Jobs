package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RareBonus;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * plugins/Jobs/jobs/*.yml を走査して {@link JobDefinition} に落とす。
 * 不正値は {@link YamlErrors} に集めて起動時にログ出力し、当該 job だけスキップする。
 */
public final class JobYamlLoader {

    private final ActionKeyDeriver keyDeriver;
    private final MatchCriteriaParser criteriaParser = new MatchCriteriaParser();
    private final RewardAmountParser rewardAmountParser = new RewardAmountParser();
    private final RareBonusParser rareBonusParser = new RareBonusParser(rewardAmountParser);
    private final VarietyPenaltyParser varietyParser = new VarietyPenaltyParser();
    private final AntiAutomationParser antiParser = new AntiAutomationParser();

    public JobYamlLoader(ActionKeyDeriver keyDeriver) {
        this.keyDeriver = keyDeriver;
    }

    public record LoadResult(List<JobDefinition> jobs, YamlErrors errors) {}

    public LoadResult loadDirectory(File directory) {
        YamlErrors errors = new YamlErrors();
        List<JobDefinition> jobs = new ArrayList<>();
        if (!directory.isDirectory()) {
            return new LoadResult(Collections.emptyList(), errors);
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return new LoadResult(Collections.emptyList(), errors);
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            try {
                JobDefinition job = loadFile(file, errors);
                if (job != null) {
                    jobs.add(job);
                }
            } catch (RuntimeException e) {
                errors.add(file.getName(), "", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
        return new LoadResult(jobs, errors);
    }

    private JobDefinition loadFile(File file, YamlErrors errors) {
        YamlConfiguration yaml;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            errors.add(file.getName(), "", "Failed to read: " + e.getMessage());
            return null;
        }

        String idRaw = yaml.getString("id");
        if (idRaw == null) {
            errors.add(file.getName(), "id", "required");
            return null;
        }
        JobId id;
        try {
            id = new JobId(idRaw);
        } catch (IllegalArgumentException e) {
            errors.add(file.getName(), "id", e.getMessage());
            return null;
        }

        String displayName = yaml.getString("display_name");
        if (displayName == null) {
            errors.add(file.getName(), "display_name", "required");
            return null;
        }

        String iconRaw = yaml.getString("icon");
        if (iconRaw == null) {
            errors.add(file.getName(), "icon", "required");
            return null;
        }
        NamespacedKey icon = NamespacedKey.fromString(iconRaw.toLowerCase(Locale.ROOT));
        if (icon == null) {
            errors.add(file.getName(), "icon", "invalid NamespacedKey: " + iconRaw);
            return null;
        }

        List<RewardEntry> rewards = loadRewards(yaml.getList("rewards"), file.getName(), errors);

        var variety = varietyParser.parse(yaml.getConfigurationSection("variety_penalty"), file.getName() + " variety_penalty");
        var anti = antiParser.parse(yaml.getConfigurationSection("anti_automation"), file.getName() + " anti_automation");

        return new JobDefinition(id, displayName, icon, rewards, variety, anti);
    }

    private List<RewardEntry> loadRewards(List<?> raw, String fileName, YamlErrors errors) {
        if (raw == null) return List.of();
        List<RewardEntry> parsed = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String path = "rewards[" + i + "]";
            Object item = raw.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                errors.add(fileName, path, "expected map");
                continue;
            }
            try {
                parsed.add(parseRewardEntry(normalizeKeys(map), path));
            } catch (YamlParseException e) {
                errors.add(fileName, path, e.getMessage());
            }
        }
        return parsed;
    }

    /**
     * SnakeYAML は YAML 1.1 互換で `on` / `off` / `yes` / `no` を boolean に解釈することがある。
     * これらが Map のキーとして現れると Boolean になるため、YAML 由来の Boolean キーを
     * 対応する文字列に戻す。
     */
    private Map<String, Object> normalizeKeys(Map<?, ?> map) {
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object key = e.getKey();
            String stringKey;
            if (key instanceof Boolean b) {
                stringKey = b ? "on" : "off";
            } else if (key == null) {
                continue;
            } else {
                stringKey = key.toString();
            }
            normalized.put(stringKey, e.getValue());
        }
        return normalized;
    }

    private RewardEntry parseRewardEntry(Map<?, ?> map, String path) {
        Object onRaw = map.get("on");
        if (!(onRaw instanceof String on)) {
            throw new YamlParseException(path + ".on: required string");
        }
        ActionType actionType = ActionType.fromYaml(on);
        MatchCriteria criteria = criteriaParser.parse(actionType, map, path);
        RewardAmount rewardAmount = rewardAmountParser.parse(map.get("reward"), path + ".reward");

        RareBonus rare = null;
        Object rareRaw = map.get("rare");
        if (rareRaw != null) {
            if (!(rareRaw instanceof Map<?, ?> rareMap)) {
                throw new YamlParseException(path + ".rare: expected map");
            }
            rare = rareBonusParser.parse(rareMap, path + ".rare");
        }

        return new RewardEntry(actionType, criteria, rewardAmount, rare, keyDeriver.derive(criteria));
    }

    /**
     * ConfigurationSection ベースで parse したい unit test 向けエントリポイント。
     * 実運用は {@link #loadDirectory(File)}。
     */
    public JobDefinition loadFromSection(ConfigurationSection section, YamlErrors errors, String fileName) {
        // 現状の実装は File ベース。テスト用にはこの API を残しておく余地はあるが、
        // 現段階では unit test 経由でファイル読み込みするため、簡易にファイル書き出しで対応する。
        throw new UnsupportedOperationException("Not implemented; use loadDirectory instead");
    }
}
