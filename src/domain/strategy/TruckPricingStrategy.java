package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

public class TruckPricingStrategy implements PricingStrategy {
    private static final double HOURLY_RATE = 50.0;

    @Override
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= 15) {
            return 0.0;
        }

        long hours = (minutes + 59) / 60;
        return hours * HOURLY_RATE;
    }

    @Override
    public String getStrategyName() {
        return "Heavy Vehicle & Truck Rate";
    }

    @Override
    public String getRateDescription() {
        return "ฟรี 15 นาทีแรก | คิด 50 บาท/ชม. (ช่องจอดพิเศษขนาดใหญ่)";
    }
}
