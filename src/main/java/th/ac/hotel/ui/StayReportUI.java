package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.model.ReportResult;
public final class StayReportUI {
    public static void displayReportResult(ReportResult<Map<String,Object>> result,Model model) {
        var preview=result.preview();var criteria=preview.criteria();String type=criteria.reportType();
        String description="IN_HOUSE".equals(type)?"ผู้พัก ณ "+criteria.asOf():("ARRIVAL".equals(type)?"ผู้เข้าพักจริงวันที่ ":"คาดว่าจะออกวันที่ ")+criteria.reportDate();
        model.addAttribute("reportResult",result);model.addAttribute("reportRows",preview.rows());model.addAttribute("criteria",description);model.addAttribute("generatedAt",preview.generatedAt());model.addAttribute("reportReady",true);
    }
}
