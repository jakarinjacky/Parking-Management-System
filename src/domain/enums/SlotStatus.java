package domain.enums;

public enum SlotStatus {
    AVAILABLE("ว่างพร้อมจอด"),
    OCCUPIED("มีรถจอดอยู่"),
    RESERVED("จองล่วงหน้า"),
    MAINTENANCE("ปิดซ่อมบำรุง");

    private final String label;

    SlotStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
