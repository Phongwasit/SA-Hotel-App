package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.model.ReportResult;
import th.ac.hotel.controller.CashReportController.CashRow;
public final class CashReportUI {
    public static void displayReportResult(ReportResult<CashRow> result,Model model) {
        var preview=result.preview();model.addAttribute("reportResult",result);model.addAttribute("reportRows",preview.rows());model.addAttribute("total",preview.total());model.addAttribute("generatedAt",preview.generatedAt());model.addAttribute("reportReady",true);
    }
}
