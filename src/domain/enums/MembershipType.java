package domain.enums;

public enum MembershipType {
    STANDARD_MEMBER("สมาชิกมาตรฐาน - จัดช่องอัตโนมัติ"),
    VIP_MEMBER("สมาชิก VIP - ให้สิทธิ์โซน VIP ก่อน"),
    EV_MEMBER("สมาชิก EV - จัดช่องชาร์จเมื่อจำเป็น");

    private final String displayName;

    MembershipType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
