package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

/**
 * Strategy Pattern: PricingStrategy
 * กำหนดสัญญาการคำนวณค่าบริการจอดรถ ซึ่งแต่ละประเภทยานพาหนะหรือโปรโมชัน
 * สามารถมีกฎทางธุรกิจ (Business Rules) แตกต่างกันได้โดยไม่ต้องแก้คลาสหลัก
 */
public interface PricingStrategy {
    double calculateFee(Duration duration, Vehicle vehicle);
    String getStrategyName();
    String getRateDescription();
}
