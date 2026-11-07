package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.PaymentController.BalanceResult;
import th.ac.hotel.controller.PaymentController.PaymentResult;
public final class PaymentUI {
    public static void displayStayBalances(BalanceResult result,Model model) {model.addAttribute("balanceResult",result);model.addAttribute("accounts",result.accounts());}
    public static String displayPaymentResult(PaymentResult result) {return "redirect:/payments/"+result.paymentId();}
}
