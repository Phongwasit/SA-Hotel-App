package th.ac.hotel.model;
import java.util.*;
public record ReportResult<T>(ReportPreview<T> preview,List<String> validationErrors,String error,int status) {
    public boolean previewReady(){return preview!=null&&validationErrors.isEmpty()&&error==null;}
}
