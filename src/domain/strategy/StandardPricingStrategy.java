package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class StandardPricingStrategy implements PricingStrategy {
    private static final int FREE_GRACE_PERIOD_MINUTES = 15;
    private static final double FIRST_HOUR_RATE = 20.0;
    private static final double SUBSEQUENT_HOUR_RATE = 30.0;

    @Override
    // [OOP: METHOD] Method calculateFee() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= FREE_GRACE_PERIOD_MINUTES) {
            return 0.0;
        }

        // ปัดเศษนาทีเป็นชั่วโมงเต็มตาม Business Rule ของที่จอดรถทั่วไป
        long hours = (minutes + 59) / 60;
        if (hours <= 1) {
            return FIRST_HOUR_RATE;
        }

        return FIRST_HOUR_RATE + ((hours - 1) * SUBSEQUENT_HOUR_RATE);
    }

    @Override
    // [OOP: METHOD] Method getStrategyName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getStrategyName() {
        return "Standard Car Rate";
    }

    @Override
    // [OOP: METHOD] Method getRateDescription() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getRateDescription() {
        return "ฟรี 15 นาทีแรก | ชม. แรก 20 บาท | ชม. ถัดไป 30 บาท/ชม.";
    }
}
