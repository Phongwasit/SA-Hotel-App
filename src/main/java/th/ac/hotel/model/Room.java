package th.ac.hotel.model;

import java.math.BigDecimal;
import java.util.List;

public record Room(int roomId, String roomNumber, String roomType, String bedType,
                   String occupancy, String cleanliness, String usability, String conditionReason,
                   BigDecimal standardNightlyRate, List<BlockingInterval> blockingIntervals) {
    public Room {
        blockingIntervals = List.copyOf(blockingIntervals);
    }

    public String bedLabel() { return "DOUBLE".equals(bedType) ? "เตียงใหญ่ 1 เตียง" : "เตียงแยก 2 เตียง"; }
    public String occupancyLabel() { return "OCCUPIED".equals(occupancy) ? "มีผู้พักตอนนี้" : "ว่างตอนนี้"; }
    public String cleanlinessLabel() { return "CLEAN".equals(cleanliness) ? "สะอาด" : "รอทำความสะอาด"; }
}
