package domain.ai;

import domain.enums.SlotType;
import java.util.Collections;
import java.util.List;

/**
 * Data Model: AIRecommendation
 * บันทึกผลลัพธ์การคัดสรรช่องจอดของ AI พร้อมเหตุผลประกอบ (Explainable AI - XAI)
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
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

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object AIRecommendation
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

    // [OOP: METHOD] Method getSlotNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getSlotNumber() { return slotNumber; }
    // [OOP: METHOD] Method getFloorNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getFloorNumber() { return floorNumber; }
    // [OOP: METHOD] Method getSlotType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public SlotType getSlotType() { return slotType; }
    // [OOP: METHOD] Method getMatchScore() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getMatchScore() { return matchScore; }
    // [OOP: METHOD] Method getConfidence() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getConfidence() { return confidence; }
    // [OOP: METHOD] Method getPrimaryReason() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getPrimaryReason() { return primaryReason; }
    // [OOP: METHOD] Method getFactors() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<String> getFactors() { return factors; }
    // [OOP: METHOD] Method getEnergyEfficiency() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getEnergyEfficiency() { return energyEfficiency; }
    // [OOP: METHOD] Method getCongestionImpact() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getCongestionImpact() { return congestionImpact; }
}
