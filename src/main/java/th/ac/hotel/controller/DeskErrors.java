package th.ac.hotel.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(assignableTypes={GuestController.class,CheckInController.class,PaymentController.class,DocumentController.class,CheckOutController.class,RoomStatusController.class,StayReportController.class,CashReportController.class})
public class DeskErrors {
    private static final Logger log=LoggerFactory.getLogger(DeskErrors.class);
    @ExceptionHandler(DeskSupport.DeskProblem.class)
    public String validation(DeskSupport.DeskProblem problem,Model model,HttpServletResponse response) {
        response.setStatus(problem.status);model.addAttribute("message",problem.getMessage());return "desk-error";
    }
    @ExceptionHandler(DataAccessException.class)
    public String database(DataAccessException problem,Model model,HttpServletResponse response) {
        boolean conflict=problem instanceof DataIntegrityViolationException;
        log.warn("Hotel operation failed: {}",problem.getClass().getSimpleName());
        response.setStatus(conflict?409:503);
        model.addAttribute("message",conflict?"ข้อมูลซ้ำหรือขัดแย้งกับรายการล่าสุด ระบบไม่ได้บันทึกรายการนี้ กรุณาโหลดข้อมูลใหม่":"โหลดหรือบันทึกข้อมูลไม่สำเร็จ รายการที่กำลังบันทึกถูกย้อนกลับ กรุณาตรวจฐานข้อมูลแล้วลองอีกครั้ง");return "desk-error";
    }
}
