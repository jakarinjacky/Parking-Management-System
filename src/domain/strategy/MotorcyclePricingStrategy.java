package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

public class MotorcyclePricingStrategy implements PricingStrategy {
    private static final int FREE_GRACE_PERIOD_MINUTES = 30;
    private static final double HOURLY_RATE = 10.0;

    @Override
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= FREE_GRACE_PERIOD_MINUTES) {
            return 0.0;
        }

        long hours = (minutes + 59) / 60;
        return hours * HOURLY_RATE;
    }

    @Override
    public String getStrategyName() {
        return "Motorcycle Economy Rate";
    }

    @Override
    public String getRateDescription() {
        return "ฟรี 30 นาทีแรก | คิด 10 บาท/ชม.";
    }
}
