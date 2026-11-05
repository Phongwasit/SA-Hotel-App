package th.ac.hotel.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** One freshly loaded stay; charges and allocations are deliberately separate. */
public record StayAccount(Map<String,Object> stay, List<Map<String,Object>> charges,
                          List<Map<String,Object>> allocations, List<Map<String,Object>> receipts) {
    public int id() { return ((Number)stay.get("stay_id")).intValue(); }
    public int roomId() { return ((Number)stay.get("room_id")).intValue(); }
    public boolean active() { return "ACTIVE".equals(stay.get("status")); }
    public BigDecimal total() { return charges.stream().map(r -> (BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public BigDecimal paid() { return allocations.stream().map(r -> (BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public BigDecimal balance() { return total().subtract(paid()); }
    public boolean paidCharge(int id) { return allocations.stream().anyMatch(r -> ((Number)r.get("charge_id")).intValue()==id); }
    public boolean settled() { return !charges.isEmpty() && balance().signum()==0 && charges.stream().allMatch(r -> paidCharge(((Number)r.get("charge_id")).intValue())); }
}
