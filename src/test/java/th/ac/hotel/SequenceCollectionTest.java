package th.ac.hotel;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import th.ac.hotel.model.*;
import static org.assertj.core.api.Assertions.*;

class SequenceCollectionTest {
    final LocalDate in=LocalDate.of(2026,11,7),out=in.plusDays(1);
    Room room(int id,String occupancy,List<BlockingInterval> intervals) {
        return new Room(id,"240"+id,"Standard","TWIN",occupancy,"CLEAN","USABLE",null,new BigDecimal("800"),intervals);
    }
    Booking booking(int id,BookingItem...items) {return new Booking(id,"Guest","080",1,LocalDateTime.of(2026,11,7,12,0),List.of(items));}
    StayAccount stay(int id,int roomId,String status) {
        return new StayAccount(Map.of("stay_id",id,"room_id",roomId,"status",status,"check_in_date",Date.valueOf(in),"expected_check_out_date",Date.valueOf(out)),List.of(),List.of(),List.of());
    }
    @Test void selectedRoomReloadPreservesOtherRoomsAndBookingsAndReplacesStaleContext() {
        var collection=new RoomCollection();var item=new BookingItem(11,1,1,in,out,"BOOKED");
        collection.storeBookingData(new BookingContext(List.of(room(1,"VACANT",List.of()),room(2,"VACANT",List.of())),List.of(booking(1,item))));
        collection.storeCheckInData(List.of(1),new CheckInContext(List.of(room(1,"OCCUPIED",List.of())),Map.of(),Set.of(1)));
        assertThat(collection.findRoom(2)).isNotNull();assertThat(collection.findBooking(1).items()).containsExactly(item);
        assertThat(collection.checkCheckInReadiness(List.of(new RoomRequest(1,in,out)),1,Map.of(),1,1,List.of())).hasSize(1);
        collection.storeCheckInData(List.of(1),new CheckInContext(List.of(room(1,"VACANT",List.of())),Map.of(),Set.of()));
        assertThat(collection.checkCheckInReadiness(List.of(new RoomRequest(1,in,out)),1,Map.of(),1,1,List.of())).isEmpty();
    }
    @Test void readinessAllowsOnlyTheSelectedMatchingBookingAndRejectsOtherBookings() {
        var collection=new RoomCollection();var item=new BookingItem(11,1,1,in,out,"BOOKED");
        collection.storeCheckInData(List.of(1),new CheckInContext(List.of(room(1,"VACANT",List.of())),Map.of(11,item),Set.of()));
        var requests=List.of(new RoomRequest(1,in,out));
        assertThat(collection.checkCheckInReadiness(requests,1,Map.of(),1,1,List.of())).hasSize(1);
        assertThat(collection.checkCheckInReadiness(requests,1,Map.of(),1,1,List.of(11))).isEmpty();
        assertThat(collection.checkCheckInReadiness(List.of(new RoomRequest(1,in,out.plusDays(1))),1,Map.of(),1,1,List.of(11))).hasSize(1);
    }
    @Test void savedCheckinConsumesOnlySelectedBookingAndBlocksTheActualStayInterval() {
        var collection=new RoomCollection();var first=new BookingItem(11,1,1,in,out,"BOOKED");var second=new BookingItem(12,1,2,in,out,"BOOKED");
        collection.storeBookingData(new BookingContext(List.of(room(1,"VACANT",List.of()),room(2,"VACANT",List.of())),List.of(booking(1,first,second))));
        collection.applySavedCheckIn(new CheckInData(List.of(stay(21,1,"ACTIVE")),List.of(room(1,"OCCUPIED",List.of())),List.of(11)));
        assertThat(collection.findBooking(1).items()).extracting(BookingItem::status).containsExactly("CHECKED_IN","BOOKED");
        assertThat(collection.getRooms("Standard",in,out,1)).isEmpty();
        assertThat(collection.findRoom(1).blockingIntervals()).extracting(BlockingInterval::id).contains(21);
        assertThat(collection.getRooms("Standard",out,out.plusDays(1),1)).hasSize(2);
    }
    @Test void savedCheckoutRemovesOnlyItsOwnStayIntervalAndRetainsFutureBookingAndOtherRoom() {
        var collection=new RoomCollection();var active=new BlockingInterval("STAY",21,in,out);var future=new BlockingInterval("BOOKING",31,out,out.plusDays(1));
        collection.storeRoomList(List.of(room(1,"OCCUPIED",List.of(active,future)),room(2,"VACANT",List.of())));
        Room dirty=new Room(1,"2401","Standard","TWIN","VACANT","DIRTY","OUT_OF_ORDER","repair",new BigDecimal("800"),List.of());
        collection.applySavedCheckOut(new CheckOutData(stay(21,1,"CHECKED_OUT"),dirty));
        assertThat(collection.findRoom(1).blockingIntervals()).containsExactly(future);
        assertThat(collection.findRoom(1).cleanliness()).isEqualTo("DIRTY");assertThat(collection.findRoom(1).usability()).isEqualTo("OUT_OF_ORDER");
        assertThat(collection.findRoom(2).occupancy()).isEqualTo("VACANT");
    }
}
