package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

public class StandardPricingStrategy implements PricingStrategy {
    private static final int FREE_GRACE_PERIOD_MINUTES = 15;
    private static final double FIRST_HOUR_RATE = 20.0;
    private static final double SUBSEQUENT_HOUR_RATE = 30.0;

    @Override
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
    public String getStrategyName() {
        return "Standard Car Rate";
    }

    @Override
    public String getRateDescription() {
        return "ฟรี 15 นาทีแรก | ชม. แรก 20 บาท | ชม. ถัดไป 30 บาท/ชม.";
    }
}
