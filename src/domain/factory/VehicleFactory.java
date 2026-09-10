package domain.factory;

import domain.enums.VehicleType;
import domain.model.Car;
import domain.model.ElectricVehicle;
import domain.model.Motorcycle;
import domain.model.Truck;
import domain.model.Vehicle;

/**
 * Factory Pattern: VehicleFactory
 * จัดการการสร้างอินสแตนซ์ของคลาสตระกูล Vehicle ตามหลัก Polymorphism
 */
public class VehicleFactory {

    public static Vehicle createVehicle(VehicleType type, String licensePlate, boolean requiresCharging) {
        if (type == null) {
            throw new IllegalArgumentException("ประเภทยานพาหนะต้องไม่เป็น null");
        }

        switch (type) {
            case CAR:
                return new Car(licensePlate);
            case MOTORCYCLE:
                return new Motorcycle(licensePlate);
            case ELECTRIC_VEHICLE:
                return new ElectricVehicle(licensePlate, requiresCharging);
            case TRUCK:
                return new Truck(licensePlate);
            default:
                throw new IllegalArgumentException("ไม่รู้จักประเภทยานพาหนะ: " + type);
        }
    }
}
