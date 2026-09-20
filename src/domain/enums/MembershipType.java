package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum MembershipType {
    STANDARD_MEMBER("สมาชิกมาตรฐาน - จัดช่องอัตโนมัติ"),
    VIP_MEMBER("สมาชิก VIP - ให้สิทธิ์โซน VIP ก่อน"),
    EV_MEMBER("สมาชิก EV - จัดช่องชาร์จเมื่อจำเป็น");

    private final String displayName;

    MembershipType(String displayName) {
        this.displayName = displayName;
    }

    // [OOP: METHOD] Method getDisplayName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getDisplayName() {
        return displayName;
    }
}
