package th.ac.hotel.model;
import java.time.*;import java.math.*;import java.util.*;
public record ReportPreview<T>(ReportCriteria criteria,LocalDateTime generatedAt,List<T> rows,BigDecimal total) {
    public ReportPreview {rows=List.copyOf(rows);}
}
