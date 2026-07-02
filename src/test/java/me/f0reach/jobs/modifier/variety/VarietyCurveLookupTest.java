package me.f0reach.jobs.modifier.variety;

import me.f0reach.jobs.domain.job.VarietyPenaltyConfig.CurvePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VarietyCurveLookupTest {

    @Test
    void picksFirstMatchingByUpToAscending() {
        VarietyCurveLookup lookup = new VarietyCurveLookup(List.of(
                new CurvePoint(0.5, 1.0),
                new CurvePoint(0.7, 0.8),
                new CurvePoint(1.01, 0.5)
        ));
        assertEquals(1.0, lookup.lookup(0.3));
        assertEquals(1.0, lookup.lookup(0.5));
        assertEquals(0.8, lookup.lookup(0.6));
        assertEquals(0.8, lookup.lookup(0.7));
        assertEquals(0.5, lookup.lookup(0.9));
        assertEquals(0.5, lookup.lookup(1.0));
    }

    @Test
    void sortsInputCurve() {
        VarietyCurveLookup lookup = new VarietyCurveLookup(List.of(
                new CurvePoint(1.01, 0.5),
                new CurvePoint(0.5, 1.0),
                new CurvePoint(0.7, 0.8)
        ));
        // 未ソートで渡しても、内部でソートされる。
        assertEquals(1.0, lookup.lookup(0.3));
        assertEquals(0.8, lookup.lookup(0.6));
        assertEquals(0.5, lookup.lookup(0.99));
    }

    @Test
    void ratioAboveTopReturnsOne() {
        VarietyCurveLookup lookup = new VarietyCurveLookup(List.of(
                new CurvePoint(0.5, 0.5)
        ));
        // ratio > up_to のとき、どこにも該当せず 1.0 を返す。
        assertEquals(0.5, lookup.lookup(0.5));
        assertEquals(1.0, lookup.lookup(0.6));
    }
}
