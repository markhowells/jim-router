package uk.co.sexeys;
/*
conda.bat activate C:\Users\msn\anaconda3\envs\twelve
copernicusmarine subset -i cmems_mod_glo_phy-cur_anfc_0.083deg_P1D-m  -v uo -v vo -t 2026-01-05 -T 2026-02-01 -Z 1 -Y 37 -x -65 -y 0 -X -10 -f current20260104
move current20250907.nc Desktop\JIM\database\grib

send GFS:00N,37N,65W,10W|0.25,0.25|0,3..192|WIND
send GFS:00N,37N,65W,10W|1.00,1.00|196,202..384|WIND
send GFS:00N,37N,65W,10W|1.0,1.0|0,6..384|WAVES

*/
public class Main {
    static final public String ROUTE = """
Search Box: 37*00'N 65*00'W 0*00'N 09*0'W
Using Polar:ELEMENTAL
Using Wind: GFS20260104193901703.grb
Using Wind: GFS20260104193901704.grb
Using Waves: GFS20260104193901705.grb
Using Current: current20260104.nc
# Porto Calero
Depart: 28*54'41"N 013*42'26"W 2026/01/10 09:00 UTC
Obstruction: 28*19'48"N 14*47'42"W;27*48'47"N 15*00'21"W;27*51'30"N 15*08'51"W;28*20'30"N 14*57'06"W;28*19'48"N 14*47'42"W TSS CANARIES EST
Obstruction: 28*33'48"N 15*39'18"W;27*58'24"N 16*12'57"W;28*03'27"N 16*19'45"W;28*38'06"N 15*46'48"W;28*33'28"N 15*39'18"W TSS CANARIES OUEST
Expand: 300 nm 360 bins 0.1 hour step
Obstruction: 42*00'00"N 035*00'00"W;20*00'00"N 025*00'00"W Force route toward Cape Verde
Leg: 13*29'05"N 058*58'06"W 500 bins of 4 nm 2 hour step
Destination: 13*15'43"N 059*38'47"W 1 nm 360 bins 0.5 hour step
# Port St Charles
""";

//    static final public String ROUTE = """
//""";

    static final public String root = "./database/";
    static final public String WindSource = "https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/"; // or "https://ftp.ncep.noaa.gov/data/nccf/com/gfs/prod/"; or "https://ftpprd.ncep.noaa.gov/data/nccf/com/gfs/prod/"
//    static final public String WindSource =  "https://ftp.ncep.noaa.gov/data/nccf/com/gfs/prod/"; // "https://ftpprd.ncep.noaa.gov/data/nccf/com/gfs/prod/"
    static final public String WindResolution = "1p00"; // 1p00, 0p50 or 0p25, 1p00. Not for Virtual Regatta
    static final public long prevailingTransitionPeriod = 15 * phys.msPerDay;
    static final public boolean sparsePolar = true;
    static final public boolean polarHighWindOnly = false; // Attempt to model in ability to sail in low wind speeds due to sails flapping.

    static final public boolean useWater = true;
    static final public boolean crossDateLine = false;
    static final public boolean useIceZone = false;
    static final public int WVSResolution  =  100000; // 250000 = slow
    static final float ChartOffsetY = 0/3600f; // +ve moves chart up
    static final float ChartOffsetX = -0/3600f; // +ve moves chart right
    static final public String REPLAY = "";
    public static final long ExpandingTimeFactor = 30; // 1/30 is kind of 2/60 = 2 degrees to ensure crossover
    static final public int numberOfFixes = 80;  // number of measurement points used in C2S bigger = slower
    static final public float searchTolerance = 100; // meters (10 for VR). How close C2S needs to get to each waypoint smaller = slower
    static final long C2SSearchPeriod =5 * phys.msPerHour; // How far along JIM that C2S places its initial destination
    static final int C2SLegs = 8; // Number of legs in C2S solution
    static final int C2SAgents = 10 * C2SLegs; // https://en.wikipedia.org/wiki/Differential_evolution
    static final float C2SCR = 0.9f ; // Cross over probability - see wiki page.
    static final float routeAspectRatio = 1.0f;
    static public float continuousFactor = 0.3f;  // factor for continuous search in DE (obsolete?)
    static public final boolean ShowRouteResolution = false  ; // false for maps that scales with screen resolution
    static public final boolean useDifferentialEvolution = false;
    public static int spinnerCounter = 0;
    public static String[] spinner = {"|","/","-","\\"};
    static public Shoreline shoreline;

    public static final long JIMCutoff = (long)(10* phys.msPerHour);
    public static final float waveWarning = 3; //m
    public static final int fontSize = 20;

    public static int minLon = 0; // Cut off limits to stop excess calculations
    public static int maxLon = 0; // Set by Search Box: in ROUTE

    public static void main(String[] args) {
        Fix.InitSpares();
        StreamFrame streamFrame = new StreamFrame();
        streamFrame.setVisible(true);
    }
}
