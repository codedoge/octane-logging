package com.example.sdksamples;

import com.impinj.octane.*;

import java.util.HashMap;
import java.util.List;

public class XArrayLocationWam {
    // WAM Role Stuff
    final SearchMode WAM_SEARCH_MODE = SearchMode.SingleTarget;
    final int WAM_SESSION = 3;   // With just one xArray could use either session 2 or 3
    // If just inventorying a few hundreds Monza tags you can use single target with suppression especially
    // when using a fast reader mode like ReaderMode.AutoSetDenseReaderDeepScan.
    // Use the next 2 commented out lines to define SingleTarget TagFocus.
        /* final SearchMode WAM_SEARCH_MODE         = SearchMode.TagFocus;
           final int WAM_SESSION                    = 1;
        */
    final int TAG_POPULATION_ESTIMATE = 2;   // Use a small estimate when using a combination of 52 xArray beams and tag suppression.
    // Typically the beam is only finding a small subset of the total number of tags.
    // Location Role Stuff
    final int XARRAY_HEIGHT = 280;  // This is for a low ceiling
    final int COMPUTE_WINDOW = 10;   // Fairly short compute window
    final int TAG_AGE_INTERVAL = 60;
    final int UPDATE_INTERVAL = 5;    // How often you receive updates in seconds
    final int LOCATION_SESSION = 2;    // When using multiple xArrays use a combination of Session 2 and 3
    // Run Timing parameters
    // Note: Increase duration for larger tag populations
    final int WAM_ROLE_DURATION = 30 * 1000;                   // Time in milliseconds to run WAM
    final int LOCATION_ROLE_DURATION = COMPUTE_WINDOW * 1000 + 1000; // Get enough time to run one compute window
    final int INTERATIONS = 2;                           // WAM and Location Roles Interations
    final int SESSION_2_OR_3_PERSISTENCE = 120 * 1000;                // If Using WAM Session 2 or 3 wait for tags to decay before restarting WAM Role
    // Create an instance of the ImpinjReader class.
    ImpinjReader reader;
    // Shared between Both Roles
    ReaderMode READER_MODE = ReaderMode.AutoSetDenseReaderDeepScan;   // Recommended moded for xArray
    // MR6 has long decay time so wait at least 2 minutes
    // Your tags may vary
    // Collect tags read and their counts per inventory round
    HashMap<String, Tag> WamTags = new HashMap<String, Tag>();
    HashMap<String, LocationReport> LocTags = new HashMap<String, LocationReport>();

    public XArrayLocationWam() {
        try {
            String hostname = System.getProperty(SampleProperties.hostname);
            if (hostname == null) {
                throw new Exception("Must specify the '" + SampleProperties.hostname + "' property");
            }
            reader = new ImpinjReader();
            //  Connect to the xArray
            reader.connect(hostname);
            System.out.println("WAM with Location: " + hostname);
            for (int i = 0; i < INTERATIONS; i++) {
                // WAM Role
                System.out.println("Running WAM. Please wait " + WAM_ROLE_DURATION / 1000 + " Sec." + " Session=" + WAM_SESSION + " Target=" + WAM_SEARCH_MODE);
                setupWamMode();
                Thread.sleep(WAM_ROLE_DURATION);  // 1 minute
                shutdownWamMode();
                System.out.println("WAM Results:  TagsRead=" + WamTags.size());
                for (Tag tag : WamTags.values()) {
                    System.out.println(tag.getEpc() + "  Ant=" + tag.getAntennaPortNumber() + "\tRSSI=" + tag.getPeakRssiInDbm());
                }
                System.out.println();
                WamTags.clear();
                // Location Role
                System.out.println("Running Location. Please wait " + LOCATION_ROLE_DURATION / 1000 + " Sec.");
                setupLocationMode();
                Thread.sleep(LOCATION_ROLE_DURATION);  // 1 minute
                shutdownLocationMode();
                System.out.println("Location Results: " + LocTags.size() + " Tags Read");
                for (LocationReport r : LocTags.values()) {
                    System.out.println(r.getEpc() + "\tReadCount=" + r.getConfidenceFactors().getReadCount() + "\tX=" + r.getLocationXCm() + "\tY=" + r.getLocationYCm());
                }
                LocTags.clear();
                System.out.println();
                // Wait for tag percistance to complete before starting WAM again
                if ((i < INTERATIONS - 1) && (WAM_SESSION == 2 || WAM_SESSION == 3)) {
                    System.out.println("Wait " + SESSION_2_OR_3_PERSISTENCE / 1000 + " Sec. for tag percistance to complete before starting WAM again");
                    Thread.sleep(SESSION_2_OR_3_PERSISTENCE);                 // 1 minute
                }
            }
            // Apply the default settings before exiting.
            reader.applyDefaultSettings();
            // Disconnect from the reader.
            reader.disconnect();
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) {
        new XArrayLocationWam();
    }

    // WAM code
    public void setupWamMode() {
        Settings settings = reader.queryDefaultSettings();
        settings.getSpatialConfig().setMode(SpatialMode.Inventory);
        settings.getReport().setIncludeAntennaPortNumber(true);
        settings.getReport().setIncludePeakRssi(true);
        // Set the reader mode, search mode and session
        settings.setReaderMode(READER_MODE);
        settings.setSearchMode(WAM_SEARCH_MODE);
        settings.setSession(WAM_SESSION);
        settings.setTagPopulationEstimate(32);  // artificially low estimate in WAM mode
        // Enable all Antennas (Beams)
        settings.getAntennas().enableAll();
        // Gen2 Filtering
        settings = setupFilter(settings);
        try {
            reader.applySettings(settings);
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        }
        reader.setTagReportListener(new TagReportListenerImplementation());
        start();
    }

    public void shutdownWamMode() {
        try {
            reader.stop();
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        }
    }

    // Location code
    public void setupLocationMode() {
        // Add Locations Report Listener
        reader.setLocationReportListener(new LocationReportListenerImplementation());
        // Start with defaults
        Settings settings = reader.queryDefaultSettings();
        // Put the xArray into location mode
        settings.getSpatialConfig().setMode(SpatialMode.Location);

        // Enable all three report types
        LocationConfig locationConfig = settings.getSpatialConfig().getLocation();
        locationConfig.setEntryReportEnabled(true);
        locationConfig.setUpdateReportEnabled(true);
        locationConfig.setExitReportEnabled(true);

        // The HeightCm of the xArray, in centimeters
        PlacementConfig placementConfig = settings.getSpatialConfig().getPlacement();
        placementConfig.setHeightCm((short) XARRAY_HEIGHT);
        placementConfig.setFacilityXLocationCm(0);
        placementConfig.setFacilityYLocationCm(0);
        placementConfig.setOrientationDegrees((short) 0);

        // Motion Window and Tag age
        locationConfig.setComputeWindowSeconds((short) COMPUTE_WINDOW);
        settings.setReaderMode(READER_MODE);
        settings.setSession(LOCATION_SESSION);
        locationConfig.setTagAgeIntervalSeconds((short) TAG_AGE_INTERVAL);
        locationConfig.setUpdateIntervalSeconds((short) UPDATE_INTERVAL);

        // Gen2 Filtering
        settings = setupFilter(settings);

        // Apply and start Reading in Location mode
        try {
            reader.applySettings(settings);
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        }
        start();
    }

    public void shutdownLocationMode() {
        stop();
    }

    private void start() {
        try {
            reader.start();
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private void stop() {
        try {
            reader.stop();
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        }
    }

    Settings setupFilter(Settings settings) {
         /* // Set filter if needed
            TagFilter t1 = settings.getFilters().getTagFilter1();
            t1.setBitCount(16);
            t1.setBitPointer(BitPointers.Epc);
            t1.setMemoryBank(MemoryBank.Epc);
            t1.setFilterOp(TagFilterOp.Match);
            t1.setTagMask("9999");
            settings.getFilters().setMode(TagFilterMode.OnlyFilter1);
         */
        return settings;
    }

    public class LocationReportListenerImplementation implements LocationReportListener {
        public void onLocationReported(ImpinjReader reader, LocationReport report) {
            // Collect tags read from the last location report
            LocTags.put(report.getEpc().toHexString(), report);
            // Comment out next line to see L every time a tag is reported
            // System.out.print("L");
        }
    }

    public class TagReportListenerImplementation implements TagReportListener {
        public void onTagReported(ImpinjReader reader, TagReport report) {
            List<Tag> tags = report.getTags();
            for (Tag t : tags) {
                // Collect tags read and their counts per inventory round
                WamTags.put(t.getEpc().toHexString(), t);
                // Comment out next line to see W every time a tag is reported
                // System.out.print("W");
            }
        }
    }
}
