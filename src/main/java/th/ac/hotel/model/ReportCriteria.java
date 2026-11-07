package th.ac.hotel.model;
import java.time.*;
public record ReportCriteria(String reportType,LocalDate reportDate,LocalDateTime asOf,LocalDateTime startAt,LocalDateTime endAt) {}
