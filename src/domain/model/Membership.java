package domain.model;

import domain.enums.MembershipType;
import java.time.LocalDate;

public class Membership {
    private final String memberId;
    private final String memberName;
    private final String licensePlate;
    private final MembershipType membershipType;
    private final LocalDate validFrom;
    private final LocalDate validUntil;

    public Membership(String memberId, String memberName, String licensePlate,
                      LocalDate validFrom, LocalDate validUntil) {
        this(memberId, memberName, licensePlate, MembershipType.STANDARD_MEMBER, validFrom, validUntil);
    }

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

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }

    public String getMemberId() { return memberId; }
    public String getMemberName() { return memberName; }
    public String getLicensePlate() { return licensePlate; }
    public MembershipType getMembershipType() { return membershipType; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
}
