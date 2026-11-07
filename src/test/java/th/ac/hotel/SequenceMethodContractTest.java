package th.ac.hotel;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Contract extracted from the teacher-approved sequence/class-detail pages. */
class SequenceMethodContractTest {
    @Test void eachSequenceOperationIsDeclaredByItsResponsibleClass() {
        Map<Class<?>,List<String>> required=new LinkedHashMap<>();
        required.put(th.ac.hotel.ui.RoomListUI.class, List.of("displaySearchResult"));
        required.put(th.ac.hotel.controller.RoomListController.class, List.of("makeSearchResult","makeValidationResult","searchAvailableRooms","validateSearchCriteria"));
        required.put(th.ac.hotel.ui.BookingUI.class, List.of("displayBookingResult","displayBookings"));
        required.put(th.ac.hotel.controller.BookingController.class, List.of("findBookings","makeBookingResult","makeValidationResult","mapToBooking","saveBookingChange","submitBookingChange","validateRequest"));
        required.put(th.ac.hotel.ui.GuestUI.class, List.of("displayProfiles","displaySaveResult","selectProfile"));
        required.put(th.ac.hotel.controller.GuestController.class, List.of("findProfiles","loadProfiles","makeProfileResult","makeValidationResult","mapToProfile","mapToProfiles","persistProfile","saveProfile","validateProfile"));
        required.put(th.ac.hotel.ui.CheckInUI.class, List.of("displayCheckInResult"));
        required.put(th.ac.hotel.controller.CheckInController.class, List.of("addBookingCheckInCommands","buildCheckInCommands","buildStayDraft","checkIn","makeCheckInResult","makeValidationResult","mapToCheckInData","saveCheckIn","validateCheckIn"));
        required.put(th.ac.hotel.ui.PaymentUI.class, List.of("displayPaymentResult","displayStayBalances"));
        required.put(th.ac.hotel.controller.PaymentController.class, List.of("calculateBalances","findStayBalances","findStayPaymentData","makeSaveFailureResult","makeSuccessResult","makeValidationResult","mapToSavedPaymentData","mapToStayPaymentData","recordPayment","savePayment","validatePayment"));
        required.put(th.ac.hotel.ui.DocumentUI.class, List.of("displayDocumentContext","displayFolioPreview","displayReceiptResult","displayReceiptWritingDetails"));
        required.put(th.ac.hotel.controller.DocumentController.class, List.of("buildFolio","checkDocumentContext","createFolioPreview","getDocumentData","loadDocumentContext","makeReceiptResult","makeValidationResult","mapToDocumentData","mapToReceiptReference","persistReceiptReference","saveReceiptReference","validateReceiptReference"));
        required.put(th.ac.hotel.ui.CheckOutUI.class, List.of("displayCheckOutDetails","displayCheckOutResult"));
        required.put(th.ac.hotel.controller.CheckOutController.class, List.of("checkOut","findCheckOutDetails","getActiveStayCheckOutData","makeCheckOutResult","makeValidationResult","mapToCheckOutData","mapToSavedCheckOutData","prepareCheckOutDetails","saveCheckOut","validateCheckOut"));
        required.put(th.ac.hotel.ui.RoomStatusUI.class, List.of("displayRoomStatus","displayRoomStatusResult"));
        required.put(th.ac.hotel.controller.RoomStatusController.class, List.of("evaluateReadiness","getRoomStatus","makeRoomStatusResult","makeRoomStatusSaveResult","makeValidationResult","mapToRoom","saveRoomCondition","updateRoomStatus","validateStatusChange"));
        required.put(th.ac.hotel.ui.StayReportUI.class, List.of("displayReportResult"));
        required.put(th.ac.hotel.controller.StayReportController.class, List.of("buildStayReport","generateStayReport","loadStayReportData","makeLoadFailureResult","makeValidationResult","mapToStayReportRows","validateCriteria"));
        required.put(th.ac.hotel.ui.CashReportUI.class, List.of("displayReportResult"));
        required.put(th.ac.hotel.controller.CashReportController.class, List.of("buildCashReport","generateCashReport","loadCashReportData","makeLoadFailureResult","makeValidationResult","mapToCashReportRows","validateCriteria"));
        required.put(th.ac.hotel.model.RoomCollection.class, List.of("applySavedBooking","applySavedCheckIn","applySavedCheckOut","applySavedRoom","checkAvailability","checkCheckInReadiness","findBookings","findRoom","getRooms","storeBookingData","storeCheckInData","storeRoomList","storeRoomStates"));
        List<String> missing=new ArrayList<>();
        required.forEach((owner,methods)-> {
            Set<String> actual=Arrays.stream(owner.getDeclaredMethods()).map(java.lang.reflect.Method::getName).collect(Collectors.toSet());
            methods.stream().filter(method->!actual.contains(method)).forEach(method->missing.add(owner.getSimpleName()+"."+method));
        });
        assertTrue(missing.isEmpty(),()->"Sequence methods missing or assigned to another owner: "+missing);
    }
}
