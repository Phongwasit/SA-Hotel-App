package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.DocumentController.*;
public final class DocumentUI {
    public static void displayDocumentContext(DocumentView result,String mode,java.time.LocalDateTime generatedAt,Model model) {model.addAttribute("document",result);model.addAttribute("mode",mode);model.addAttribute("generatedAt",generatedAt);}
    public static void displayFolioPreview(FolioPreview preview,Model model) {model.addAttribute("document",preview.documentData());model.addAttribute("generatedAt",preview.generatedAt());}
    public static void displayReceiptWritingDetails(DocumentView documentData,Model model) {model.addAttribute("document",documentData);model.addAttribute("mode","REFERENCE");}
    public static String displayReceiptResult(ReceiptResult result) {return "redirect:/documents?referenceId="+result.referenceId()+"&mode=REFERENCE&saved=true";}
}
