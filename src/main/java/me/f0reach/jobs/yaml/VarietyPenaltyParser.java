package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VarietyPenaltyParser {

    public VarietyPenaltyConfig parse(ConfigurationSection section, String path) {
        if (section == null) {
            return VarietyPenaltyConfig.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        if (!enabled) {
            return VarietyPenaltyConfig.disabled();
        }
        int window = section.getInt("window", 30);
        List<VarietyPenaltyConfig.CurvePoint> curve = parseCurve(section.getList("curve"), path + ".curve");
        String disclosed = section.getString("disclosed_message", null);
        boolean hideNumbers = section.getBoolean("hide_numbers", false);
        return new VarietyPenaltyConfig(true, window, curve, disclosed, hideNumbers);
    }

    private List<VarietyPenaltyConfig.CurvePoint> parseCurve(List<?> raw, String path) {
        if (raw == null || raw.isEmpty()) {
            throw new YamlParseException(path + ": required when variety_penalty.enabled=true");
        }
        List<VarietyPenaltyConfig.CurvePoint> points = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object item = raw.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                throw new YamlParseException(path + "[" + i + "]: expected map");
            }
            Object upTo = map.get("up_to");
            Object mult = map.get("multiplier");
            if (!(upTo instanceof Number) || !(mult instanceof Number)) {
                throw new YamlParseException(path + "[" + i + "]: up_to and multiplier are required numbers");
            }
            points.add(new VarietyPenaltyConfig.CurvePoint(
                    ((Number) upTo).doubleValue(),
                    ((Number) mult).doubleValue()
            ));
        }
        points.sort(java.util.Comparator.comparingDouble(VarietyPenaltyConfig.CurvePoint::upTo));
        return points;
    }
}
