package domain.model;

import domain.enums.MembershipType;
import java.time.LocalDate;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Membership {
    private final String memberId;
    private final String memberName;
    private final String licensePlate;
    private final MembershipType membershipType;
    private final LocalDate validFrom;
    private final LocalDate validUntil;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Membership
    public Membership(String memberId, String memberName, String licensePlate,
                      LocalDate validFrom, LocalDate validUntil) {
        this(memberId, memberName, licensePlate, MembershipType.STANDARD_MEMBER, validFrom, validUntil);
    }

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Membership
    public Membership(String memberId, String memberName, String licensePlate,
                      MembershipType membershipType, LocalDate validFrom, LocalDate validUntil) {
        if (memberId == null || memberName == null || licensePlate == null || membershipType == null || validFrom == null || validUntil == null) {
            throw new IllegalArgumentException("ข้อมูลสมาชิกไม่ครบถ้วน");
        }
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("วันหมดอายุต้องไม่ก่อนวันเริ่มต้น");
        }
        this.memberId = memberId;
        this.memberName = memberName;
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.membershipType = membershipType;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    // [OOP: METHOD] Method isValidOn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }

    // [OOP: METHOD] Method getMemberId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getMemberId() { return memberId; }
    // [OOP: METHOD] Method getMemberName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getMemberName() { return memberName; }
    // [OOP: METHOD] Method getLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLicensePlate() { return licensePlate; }
    // [OOP: METHOD] Method getMembershipType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public MembershipType getMembershipType() { return membershipType; }
    // [OOP: METHOD] Method getValidFrom() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDate getValidFrom() { return validFrom; }
    // [OOP: METHOD] Method getValidUntil() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDate getValidUntil() { return validUntil; }
}
