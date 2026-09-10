package domain.ai;

import domain.enums.SlotType;
import java.util.Collections;
import java.util.List;

/**
 * Data Model: AIRecommendation
 * บันทึกผลลัพธ์การคัดสรรช่องจอดของ AI พร้อมเหตุผลประกอบ (Explainable AI - XAI)
 */
public class AIRecommendation {
    private final String slotNumber;
    private final int floorNumber;
    private final SlotType slotType;
    private final double matchScore;       // คะแนนความเหมาะสม 0.0 - 100.0%
    private final double confidence;       // ความเชื่อมั่นของโมเดล 0.0 - 1.0 (เช่น 0.98)
    private final String primaryReason;    // เหตุผลหลัก (ภาษาไทย)
    private final List<String> factors;    // ปัจจัยย่อยที่ AI นำมาคำนวณ
    private final String energyEfficiency; // ประสิทธิภาพพลังงาน/การเดินเท้า
    private final String congestionImpact; // ผลกระทบต่อการระบายการจราจร

    public AIRecommendation(String slotNumber,
                            int floorNumber,
                            SlotType slotType,
                            double matchScore,
                            double confidence,
                            String primaryReason,
                            List<String> factors,
                            String energyEfficiency,
                            String congestionImpact) {
        this.slotNumber = slotNumber;
        this.floorNumber = floorNumber;
        this.slotType = slotType;
        this.matchScore = matchScore;
        this.confidence = confidence;
        this.primaryReason = primaryReason;
        this.factors = factors != null ? factors : Collections.emptyList();
        this.energyEfficiency = energyEfficiency;
        this.congestionImpact = congestionImpact;
    }

    public String getSlotNumber() { return slotNumber; }
    public int getFloorNumber() { return floorNumber; }
    public SlotType getSlotType() { return slotType; }
    public double getMatchScore() { return matchScore; }
    public double getConfidence() { return confidence; }
    public String getPrimaryReason() { return primaryReason; }
    public List<String> getFactors() { return factors; }
    public String getEnergyEfficiency() { return energyEfficiency; }
    public String getCongestionImpact() { return congestionImpact; }
}
