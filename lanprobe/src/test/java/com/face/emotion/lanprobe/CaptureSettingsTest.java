package com.face.emotion.lanprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureSettingsTest {
    @Test public void defaultsAreTwoSecondsSixteenFrames(){CaptureSettings c=CaptureSettings.DEFAULT;assertEquals(16,c.frames);assertEquals(2000,c.durationMs);assertEquals(32000,c.rate);assertEquals(1875,c.slotMs(15));}
    @Test public void rationalScheduleDoesNotDrift(){CaptureSettings c=new CaptureSettings(3,32000,2);assertEquals(334,c.slotMs(1));assertEquals(667,c.slotMs(2));assertEquals(1000,c.slotMs(3));assertEquals(1,c.next(0,334));assertEquals(2,c.next(0,335));}
    @Test public void skipsMissedSlots(){CaptureSettings c=CaptureSettings.DEFAULT;assertEquals(4,c.next(1,401));assertEquals(16,c.next(15,2000));}
    @Test public void allRatesAccepted(){for(int rate:CaptureSettings.RATES)assertEquals(rate,new CaptureSettings(8,rate,2).rate);}
    @Test(expected=IllegalArgumentException.class)public void rejectsZeroFps(){new CaptureSettings(0,32000,2);}
    @Test(expected=IllegalArgumentException.class)public void rejectsHugeFrameCount(){new CaptureSettings(30,32000,60);}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnknownRate(){new CaptureSettings(8,12345,2);}
    @Test(expected=IllegalArgumentException.class)public void rejectsZeroDuration(){new CaptureSettings(8,32000,0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsLongDuration(){new CaptureSettings(8,32000,61);}
}
