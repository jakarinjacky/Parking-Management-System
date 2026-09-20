package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

/**
 * Strategy Pattern: PricingStrategy
 * กำหนดสัญญาการคำนวณค่าบริการจอดรถ ซึ่งแต่ละประเภทยานพาหนะหรือโปรโมชัน
 * สามารถมีกฎทางธุรกิจ (Business Rules) แตกต่างกันได้โดยไม่ต้องแก้คลาสหลัก
 */
// [OOP: INTERFACE] อินเทอร์เฟซ: สัญญาที่กำหนดว่า class ที่นำไปใช้ต้องมี method ใดบ้าง
public interface PricingStrategy {
    // [OOP: METHOD] Method calculateFee() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    double calculateFee(Duration duration, Vehicle vehicle);
    // [OOP: METHOD] Method getStrategyName() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    String getStrategyName();
    // [OOP: METHOD] Method getRateDescription() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    String getRateDescription();
}
