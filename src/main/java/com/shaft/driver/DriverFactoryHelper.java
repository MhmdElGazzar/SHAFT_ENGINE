package com.shaft.driver;

import com.epam.healenium.SelfHealingDriver;
import com.shaft.api.RestActions;
import com.shaft.cli.FileActions;
import com.shaft.cli.TerminalActions;
import com.shaft.driver.DriverFactory.DriverType;
import com.shaft.gui.browser.BrowserActions;
import com.shaft.gui.element.JavaScriptWaitManager;
import com.shaft.gui.video.RecordManager;
import com.shaft.tools.io.PropertyFileManager;
import com.shaft.tools.io.ReportManager;
import com.shaft.tools.io.ReportManagerHelper;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.sikuli.script.App;
import org.testng.Assert;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DriverFactoryHelper {
    // TODO: implement pass and fail actions to enable initial factory method screenshot and append it to animated GIF
    private static final Map<String, Map<String, WebDriver>> drivers = new HashMap<>();
    private static Boolean AUTO_MAXIMIZE;
    private static Boolean HEADLESS_EXECUTION;
    private static String EXECUTION_ADDRESS;
    // local OR hub ip:port
    private static String TARGET_HUB_URL;
    // Windows-64 | Linux-64 | Mac-64
    private static String TARGET_DRIVER_NAME;
    // Default | MozillaFirefox | MicrosoftInternetExplorer | GoogleChrome |
    // MicrosoftEdge | Safari
    private static String TARGET_MOBILE_BROWSER_NAME;
    private static final String WEBDRIVERMANAGER_MESSAGE = "Identifying OS/Driver combination and selecting the correct driver version automatically. Please note that if a new driver executable will be downloaded it may take some time...";
    private static int PAGE_LOAD_TIMEOUT;
    private static int SCRIPT_TIMEOUT;
    private static int IMPLICIT_WAIT_TIMEOUT;
    private static Boolean WAIT_IMPLICITLY;
    private static Boolean DRIVER_OBJECT_SINGLETON;
    private static String customDriverPath;
    private static String customDriverName;
    private static String targetOperatingSystem;
    private static ThreadLocal<WebDriver> driver = new ThreadLocal<>();

    // supported driver options
    private static ChromeOptions chOptions;
    private static FirefoxOptions ffOptions;
    private static SafariOptions sfOptions;
    private static EdgeOptions edOptions;
    private static InternetExplorerOptions ieOptions;
    private static DesiredCapabilities appiumCapabilities;

    // kill-switch
    private static boolean killSwitch = false;

    private DriverFactoryHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks to see if the execution is a web-based execution
     *
     * @return true if it's a web-based execution
     */
    public static boolean isWebExecution() {
        return !isMobileExecution();
    }

    /**
     * Checks to see if the execution is a mobile (native or web) execution
     *
     * @return true if it's a mobile (native or web) execution
     */
    public static boolean isMobileExecution() {
        return "Android".equalsIgnoreCase(targetOperatingSystem) || "iOS".equalsIgnoreCase(targetOperatingSystem);
    }

    /**
     * Checks to see if the execution is a mobile-web execution
     *
     * @return true if it's a mobile mobile-web execution
     */
    public static boolean isMobileWebExecution() {
        return isMobileExecution() && TARGET_MOBILE_BROWSER_NAME != null && !"".equals(TARGET_MOBILE_BROWSER_NAME);
    }

    /**
     * Checks to see if the execution is a mobile-native execution
     *
     * @return true if it's a mobile mobile-native execution
     */
    public static boolean isMobileNativeExecution() {
        return isMobileExecution() && (TARGET_MOBILE_BROWSER_NAME == null || "".equals(TARGET_MOBILE_BROWSER_NAME));
    }

    /**
     * Read the target driver value from the execution.properties file
     *
     * @return a new driver instance
     */
    protected static WebDriver getDriver() {
        return getDriver(TARGET_DRIVER_NAME, null);
    }

    /**
     * Creates a new driver instance
     *
     * @param driverType one of the supported driver types
     * @return a new driver instance
     */
    protected static WebDriver getDriver(DriverType driverType) {
        return getDriver(driverType.getValue(), null);
    }

    /**
     * Creates a new driver instance with custom driver options
     *
     * @param driverType          one of the supported driver types
     * @param customDriverOptions the custom options that will be used to create this new driver instance
     * @return a new driver instance
     */
    protected static WebDriver getDriver(DriverType driverType, MutableCapabilities customDriverOptions) {
        return getDriver(driverType.getValue(), customDriverOptions);
    }

    /**
     * Attaches your SikuliActions to a specific Application instance
     *
     * @param applicationName the name or partial name of the currently opened application window that you want to attach to
     * @return a sikuli App instance that can be used to perform SikuliActions
     */
    protected static App getSikuliApp(String applicationName) {
        initializeSystemProperties();
        var myapp = new App(applicationName);
        myapp.waitForWindow(Integer.parseInt(System.getProperty("browserNavigationTimeout")));
        myapp.focus();
        ReportManager.log("Opened app: \"" + myapp.getName() + "\"...");
        return myapp;
    }

    /**
     * Creates a new API instance to facilitate using the Rest Actions Library
     *
     * @param serviceURI the base URI of the target web service
     * @return rest actions instance that can be used to chain and build your api request
     */
    protected static RestActions getAPIDriver(String serviceURI) {
        return new RestActions(serviceURI);
    }

    /**
     * Creates a new local Terminal instance to facilitate using the Terminal Actions Library
     *
     * @return local terminal driver instance
     */
    protected static TerminalActions getTerminalDriver() {
        return new TerminalActions();
    }



    /**
     * Terminates the desired sikuli app instance
     *
     * @param application a sikuli App instance that can be used to perform SikuliActions
     */
    protected static void closeSikuliApp(App application) {
        ReportManager.log("Closing app: \"" + application.getName() + "\"...");
        application.close();
    }

    /**
     * Close all open driver instances.
     */
    protected static synchronized void closeAllDrivers() {
        if (System.getProperty("videoParams_scope").trim().equals("DriverSession")) {
            RecordManager.attachVideoRecording();
        }
        if (!drivers.entrySet().isEmpty()) {
            for (Entry<String, Map<String, WebDriver>> entry : drivers.entrySet()) {
                for (Entry<String, WebDriver> driverEntry : entry.getValue().entrySet()) {
                    WebDriver targetDriver = driverEntry.getValue();
                    if (((RemoteWebDriver) targetDriver).getSessionId() != null) {
                        attachWebDriverLogs(targetDriver);
                        attemptToCloseOrQuitDriver(targetDriver, false);
                        attemptToCloseOrQuitDriver(targetDriver, true);
                    }
                }
            }
            driver = new ThreadLocal<>();
            drivers.clear();
            ReportManager.log("Successfully Closed All Drivers.");
        }
    }

    /**
     * Checks to see that there are currently no opened driver sessions
     *
     * @return true if there are currently no opened driver sessions
     */
    public static Boolean isDriversListEmpty() {
        return drivers.entrySet().isEmpty();
    }

    /**
     * Gets the number of currently opened driver sessions
     *
     * @return an int value representing the number of currently opened driver sessions
     */
    public static int getActiveDriverSessions() {
        return drivers.entrySet().size();
    }

    /**
     * Checks to see if the kill switch is active
     *
     * @return true if the kiss switch is active
     */
    public static boolean isKillSwitch() {
        return killSwitch;
    }

    /**
     * Closes the driver associated with the provided hashCode
     *
     * @param hashCode of the target driver instance that will be closed
     */
    public static synchronized void closeDriver(int hashCode) {
        if (System.getProperty("videoParams_scope").trim().equals("DriverSession")) {
            RecordManager.attachVideoRecording();
        }
        if (!drivers.entrySet().isEmpty()) {
            for (Entry<String, Map<String, WebDriver>> entry : drivers.entrySet()) {
                if (entry.getKey().contains(String.valueOf(hashCode))) {
                    WebDriver targetDriver = entry.getValue().get(targetOperatingSystem);
                    attachWebDriverLogs(targetDriver);
                    attemptToCloseOrQuitDriver(targetDriver, true);
                    driver.remove();
                }
            }
        }
    }

    private static void failAction(String testData, Throwable... rootCauseException) {
        String actionName = Thread.currentThread().getStackTrace()[2].getMethodName();
        String message = "Driver Factory Action \"" + actionName + "\" failed.";
        if (testData != null) {
            message = message + " With the following test data \"" + testData + "\".";
        }
        if (rootCauseException != null && rootCauseException.length >= 1) {
            ReportManagerHelper.log(rootCauseException[0]);
            Assert.fail(message, rootCauseException[0]);
        } else {
            ReportManager.log(message);
            Assert.fail(message);
        }
    }

    static DriverType getDriverTypeFromName(String driverName) {
        int values = DriverType.values().length;
        for (var i = 0; i < values; i++) {
            if (Arrays.asList(DriverType.values()).get(i).getValue().equalsIgnoreCase(driverName.trim())) {
                return Arrays.asList(DriverType.values()).get(i);
            }
        }
        failAction("Unsupported Driver Type \"" + driverName + "\".");
        return DriverType.DESKTOP_CHROME;
    }

    private static OperatingSystemType getOperatingSystemFromName(String operatingSystemName) {
        int values = OperatingSystemType.values().length;
        for (var i = 0; i < values; i++) {
            if (Arrays.asList(OperatingSystemType.values()).get(i).getValue()
                    .equalsIgnoreCase(operatingSystemName.trim())) {
                return Arrays.asList(OperatingSystemType.values()).get(i);
            }
        }
        failAction("Unsupported Operating System \"" + targetOperatingSystem + "\".");
        return OperatingSystemType.LINUX;
    }

    /**
     * Check cross-compatibility between the selected operating system and driver
     * and report in case they are not compatible
     */
    private static void checkDriverOSCrossCompatibility(String driverName) {
        var isCompatibleDriver = false;
        var driverType = getDriverTypeFromName(driverName);

        OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);

        switch (operatingSystem) {
            case WINDOWS:
                if (driverType.equals(DriverType.DESKTOP_FIREFOX) || driverType.equals(DriverType.DESKTOP_CHROME)
                        || driverType.equals(DriverType.DESKTOP_INTERNET_EXPLORER) || driverType.equals(DriverType.DESKTOP_EDGE)) {
                    isCompatibleDriver = true;
                }
                break;
            case LINUX:
                if (driverType.equals(DriverType.DESKTOP_FIREFOX) || driverType.equals(DriverType.DESKTOP_CHROME) || driverType.equals(DriverType.DESKTOP_EDGE)) {
                    isCompatibleDriver = true;
                }
                break;
            case MACOS:
                if (driverType.equals(DriverType.DESKTOP_FIREFOX) || driverType.equals(DriverType.DESKTOP_CHROME)
                        || driverType.equals(DriverType.DESKTOP_SAFARI)) {
                    isCompatibleDriver = true;
                }
                break;
            case ANDROID:
                if (driverType.equals(DriverType.APPIUM_CHROME) || driverType.equals(DriverType.APPIUM_CHROMIUM)
                        || driverType.equals(DriverType.APPIUM_BROWSER)) {
                    isCompatibleDriver = true;
                }
                break;
            case IOS:
                if (driverType.equals(DriverType.DESKTOP_SAFARI)) {
                    isCompatibleDriver = true;
                }
                break;

            default:
                failAction("Unsupported Operating System \"" + targetOperatingSystem + "\".");
                break;
        }

        if (Boolean.FALSE.equals(isCompatibleDriver)) {
            failAction("Unsupported Driver Type \"" + driverType + "\" for this Operating System \""
                    + targetOperatingSystem + "\".");
        }

    }

    private static String setDriversExtecutableFileExtension() {
        OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);
        if (operatingSystem.equals(OperatingSystemType.WINDOWS)) {
            return ".exe";
        } else {
            return "";
        }
    }

    private static void setDriverOptions(String driverName, MutableCapabilities customDriverOptions) {
        String downloadsFolderPath = FileActions.getInstance().getAbsolutePath(System.getProperty("downloadsFolderPath"));
        var driverType = getDriverTypeFromName(driverName);

        //get proxy server
        // Proxy server settings | testing behind a proxy
        String PROXY_SERVER_SETTINGS = System.getProperty("com.SHAFT.proxySettings");

        //https://github.com/GoogleChrome/chrome-launcher/blob/master/docs/chrome-flags-for-tools.md#--enable-automation
        switch (driverType) {
            case DESKTOP_FIREFOX -> {
                // https://developer.mozilla.org/en-US/docs/Web/WebDriver/Capabilities/firefoxOptions
                ffOptions = new FirefoxOptions();
                if (customDriverOptions != null) {
                    ffOptions = ffOptions.merge(customDriverOptions);
                }
                var ffProfile = new FirefoxProfile();
                ffProfile.setPreference("browser.download.dir", downloadsFolderPath);
                ffProfile.setPreference("browser.download.folderList", 2);
                ffProfile.setPreference("browser.helperApps.neverAsk.saveToDisk",
                        "application/vnd.hzn-3d-crossword;video/3gpp;video/3gpp2;application/vnd.mseq;application/vnd.3m.post-it-notes;application/vnd.3gpp.pic-bw-large;application/vnd.3gpp.pic-bw-small;application/vnd.3gpp.pic-bw-var;application/vnd.3gp2.tcap;application/x-7z-compressed;application/x-abiword;application/x-ace-compressed;application/vnd.americandynamics.acc;application/vnd.acucobol;application/vnd.acucorp;audio/adpcm;application/x-authorware-bin;application/x-athorware-map;application/x-authorware-seg;application/vnd.adobe.air-application-installer-package+zip;application/x-shockwave-flash;application/vnd.adobe.fxp;application/pdf;application/vnd.cups-ppd;application/x-director;applicaion/vnd.adobe.xdp+xml;application/vnd.adobe.xfdf;audio/x-aac;application/vnd.ahead.space;application/vnd.airzip.filesecure.azf;application/vnd.airzip.filesecure.azs;application/vnd.amazon.ebook;application/vnd.amiga.ami;applicatin/andrew-inset;application/vnd.android.package-archive;application/vnd.anser-web-certificate-issue-initiation;application/vnd.anser-web-funds-transfer-initiation;application/vnd.antix.game-component;application/vnd.apple.installe+xml;application/applixware;application/vnd.hhe.lesson-player;application/vnd.aristanetworks.swi;text/x-asm;application/atomcat+xml;application/atomsvc+xml;application/atom+xml;application/pkix-attr-cert;audio/x-aiff;video/x-msvieo;application/vnd.audiograph;image/vnd.dxf;model/vnd.dwf;text/plain-bas;application/x-bcpio;application/octet-stream;image/bmp;application/x-bittorrent;application/vnd.rim.cod;application/vnd.blueice.multipass;application/vnd.bm;application/x-sh;image/prs.btif;application/vnd.businessobjects;application/x-bzip;application/x-bzip2;application/x-csh;text/x-c;application/vnd.chemdraw+xml;text/css;chemical/x-cdx;chemical/x-cml;chemical/x-csml;application/vn.contact.cmsg;application/vnd.claymore;application/vnd.clonk.c4group;image/vnd.dvb.subtitle;application/cdmi-capability;application/cdmi-container;application/cdmi-domain;application/cdmi-object;application/cdmi-queue;applicationvnd.cluetrust.cartomobile-config;application/vnd.cluetrust.cartomobile-config-pkg;image/x-cmu-raster;model/vnd.collada+xml;text/csv;application/mac-compactpro;application/vnd.wap.wmlc;image/cgm;x-conference/x-cooltalk;image/x-cmx;application/vnd.xara;application/vnd.cosmocaller;application/x-cpio;application/vnd.crick.clicker;application/vnd.crick.clicker.keyboard;application/vnd.crick.clicker.palette;application/vnd.crick.clicker.template;application/vn.crick.clicker.wordbank;application/vnd.criticaltools.wbs+xml;application/vnd.rig.cryptonote;chemical/x-cif;chemical/x-cmdf;application/cu-seeme;application/prs.cww;text/vnd.curl;text/vnd.curl.dcurl;text/vnd.curl.mcurl;text/vnd.crl.scurl;application/vnd.curl.car;application/vnd.curl.pcurl;application/vnd.yellowriver-custom-menu;application/dssc+der;application/dssc+xml;application/x-debian-package;audio/vnd.dece.audio;image/vnd.dece.graphic;video/vnd.dec.hd;video/vnd.dece.mobile;video/vnd.uvvu.mp4;video/vnd.dece.pd;video/vnd.dece.sd;video/vnd.dece.video;application/x-dvi;application/vnd.fdsn.seed;application/x-dtbook+xml;application/x-dtbresource+xml;application/vnd.dvb.ait;applcation/vnd.dvb.service;audio/vnd.digital-winds;image/vnd.djvu;application/xml-dtd;application/vnd.dolby.mlp;application/x-doom;application/vnd.dpgraph;audio/vnd.dra;application/vnd.dreamfactory;audio/vnd.dts;audio/vnd.dts.hd;imag/vnd.dwg;application/vnd.dynageo;application/ecmascript;application/vnd.ecowin.chart;image/vnd.fujixerox.edmics-mmr;image/vnd.fujixerox.edmics-rlc;application/exi;application/vnd.proteus.magazine;application/epub+zip;message/rfc82;application/vnd.enliven;application/vnd.is-xpr;image/vnd.xiff;application/vnd.xfdl;application/emma+xml;application/vnd.ezpix-album;application/vnd.ezpix-package;image/vnd.fst;video/vnd.fvt;image/vnd.fastbidsheet;application/vn.denovo.fcselayout-link;video/x-f4v;video/x-flv;image/vnd.fpx;image/vnd.net-fpx;text/vnd.fmi.flexstor;video/x-fli;application/vnd.fluxtime.clip;application/vnd.fdf;text/x-fortran;application/vnd.mif;application/vnd.framemaker;imae/x-freehand;application/vnd.fsc.weblaunch;application/vnd.frogans.fnc;application/vnd.frogans.ltf;application/vnd.fujixerox.ddd;application/vnd.fujixerox.docuworks;application/vnd.fujixerox.docuworks.binder;application/vnd.fujitu.oasys;application/vnd.fujitsu.oasys2;application/vnd.fujitsu.oasys3;application/vnd.fujitsu.oasysgp;application/vnd.fujitsu.oasysprs;application/x-futuresplash;application/vnd.fuzzysheet;image/g3fax;application/vnd.gmx;model/vn.gtw;application/vnd.genomatix.tuxedo;application/vnd.geogebra.file;application/vnd.geogebra.tool;model/vnd.gdl;application/vnd.geometry-explorer;application/vnd.geonext;application/vnd.geoplan;application/vnd.geospace;applicatio/x-font-ghostscript;application/x-font-bdf;application/x-gtar;application/x-texinfo;application/x-gnumeric;application/vnd.google-earth.kml+xml;application/vnd.google-earth.kmz;application/vnd.grafeq;image/gif;text/vnd.graphviz;aplication/vnd.groove-account;application/vnd.groove-help;application/vnd.groove-identity-message;application/vnd.groove-injector;application/vnd.groove-tool-message;application/vnd.groove-tool-template;application/vnd.groove-vcar;video/h261;video/h263;video/h264;application/vnd.hp-hpid;application/vnd.hp-hps;application/x-hdf;audio/vnd.rip;application/vnd.hbci;application/vnd.hp-jlyt;application/vnd.hp-pcl;application/vnd.hp-hpgl;application/vnd.yamaha.h-script;application/vnd.yamaha.hv-dic;application/vnd.yamaha.hv-voice;application/vnd.hydrostatix.sof-data;application/hyperstudio;application/vnd.hal+xml;text/html;application/vnd.ibm.rights-management;application/vnd.ibm.securecontainer;text/calendar;application/vnd.iccprofile;image/x-icon;application/vnd.igloader;image/ief;application/vnd.immervision-ivp;application/vnd.immervision-ivu;application/reginfo+xml;text/vnd.in3d.3dml;text/vnd.in3d.spot;mode/iges;application/vnd.intergeo;application/vnd.cinderella;application/vnd.intercon.formnet;application/vnd.isac.fcs;application/ipfix;application/pkix-cert;application/pkixcmp;application/pkix-crl;application/pkix-pkipath;applicaion/vnd.insors.igm;application/vnd.ipunplugged.rcprofile;application/vnd.irepository.package+xml;text/vnd.sun.j2me.app-descriptor;application/java-archive;application/java-vm;application/x-java-jnlp-file;application/java-serializd-object;text/x-java-source,java;application/javascript;application/json;application/vnd.joost.joda-archive;video/jpm;image/jpeg;video/jpeg;application/vnd.kahootz;application/vnd.chipnuts.karaoke-mmd;application/vnd.kde.karbon;aplication/vnd.kde.kchart;application/vnd.kde.kformula;application/vnd.kde.kivio;application/vnd.kde.kontour;application/vnd.kde.kpresenter;application/vnd.kde.kspread;application/vnd.kde.kword;application/vnd.kenameaapp;applicatin/vnd.kidspiration;application/vnd.kinar;application/vnd.kodak-descriptor;application/vnd.las.las+xml;application/x-latex;application/vnd.llamagraphics.life-balance.desktop;application/vnd.llamagraphics.life-balance.exchange+xml;application/vnd.jam;application/vnd.lotus-1-2-3;application/vnd.lotus-approach;application/vnd.lotus-freelance;application/vnd.lotus-notes;application/vnd.lotus-organizer;application/vnd.lotus-screencam;application/vnd.lotus-wordro;audio/vnd.lucent.voice;audio/x-mpegurl;video/x-m4v;application/mac-binhex40;application/vnd.macports.portpkg;application/vnd.osgeo.mapguide.package;application/marc;application/marcxml+xml;application/mxf;application/vnd.wolfrm.player;application/mathematica;application/mathml+xml;application/mbox;application/vnd.medcalcdata;application/mediaservercontrol+xml;application/vnd.mediastation.cdkey;application/vnd.mfer;application/vnd.mfmp;model/mesh;appliation/mads+xml;application/mets+xml;application/mods+xml;application/metalink4+xml;application/vnd.ms-powerpoint.template.macroenabled.12;application/vnd.ms-word.document.macroenabled.12;application/vnd.ms-word.template.macroenabed.12;application/vnd.mcd;application/vnd.micrografx.flo;application/vnd.micrografx.igx;application/vnd.eszigno3+xml;application/x-msaccess;video/x-ms-asf;application/x-msdownload;application/vnd.ms-artgalry;application/vnd.ms-ca-compressed;application/vnd.ms-ims;application/x-ms-application;application/x-msclip;image/vnd.ms-modi;application/vnd.ms-fontobject;application/vnd.ms-excel;application/vnd.ms-excel.addin.macroenabled.12;application/vnd.ms-excelsheet.binary.macroenabled.12;application/vnd.ms-excel.template.macroenabled.12;application/vnd.ms-excel.sheet.macroenabled.12;application/vnd.ms-htmlhelp;application/x-mscardfile;application/vnd.ms-lrm;application/x-msmediaview;aplication/x-msmoney;application/vnd.openxmlformats-officedocument.presentationml.presentation;application/vnd.openxmlformats-officedocument.presentationml.slide;application/vnd.openxmlformats-officedocument.presentationml.slideshw;application/vnd.openxmlformats-officedocument.presentationml.template;application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;application/vnd.openxmlformats-officedocument.spreadsheetml.template;application/vnd.openxmformats-officedocument.wordprocessingml.document;application/vnd.openxmlformats-officedocument.wordprocessingml.template;application/x-msbinder;application/vnd.ms-officetheme;application/onenote;audio/vnd.ms-playready.media.pya;vdeo/vnd.ms-playready.media.pyv;application/vnd.ms-powerpoint;application/vnd.ms-powerpoint.addin.macroenabled.12;application/vnd.ms-powerpoint.slide.macroenabled.12;application/vnd.ms-powerpoint.presentation.macroenabled.12;appliation/vnd.ms-powerpoint.slideshow.macroenabled.12;application/vnd.ms-project;application/x-mspublisher;application/x-msschedule;application/x-silverlight-app;application/vnd.ms-pki.stl;application/vnd.ms-pki.seccat;application/vn.visio;video/x-ms-wm;audio/x-ms-wma;audio/x-ms-wax;video/x-ms-wmx;application/x-ms-wmd;application/vnd.ms-wpl;application/x-ms-wmz;video/x-ms-wmv;video/x-ms-wvx;application/x-msmetafile;application/x-msterminal;application/msword;application/x-mswrite;application/vnd.ms-works;application/x-ms-xbap;application/vnd.ms-xpsdocument;audio/midi;application/vnd.ibm.minipay;application/vnd.ibm.modcap;application/vnd.jcp.javame.midlet-rms;application/vnd.tmobile-ivetv;application/x-mobipocket-ebook;application/vnd.mobius.mbk;application/vnd.mobius.dis;application/vnd.mobius.plc;application/vnd.mobius.mqy;application/vnd.mobius.msl;application/vnd.mobius.txf;application/vnd.mobius.daf;tex/vnd.fly;application/vnd.mophun.certificate;application/vnd.mophun.application;video/mj2;audio/mpeg;video/vnd.mpegurl;video/mpeg;application/mp21;audio/mp4;video/mp4;application/mp4;application/vnd.apple.mpegurl;application/vnd.msician;application/vnd.muvee.style;application/xv+xml;application/vnd.nokia.n-gage.data;application/vnd.nokia.n-gage.symbian.install;application/x-dtbncx+xml;application/x-netcdf;application/vnd.neurolanguage.nlu;application/vnd.na;application/vnd.noblenet-directory;application/vnd.noblenet-sealer;application/vnd.noblenet-web;application/vnd.nokia.radio-preset;application/vnd.nokia.radio-presets;text/n3;application/vnd.novadigm.edm;application/vnd.novadim.edx;application/vnd.novadigm.ext;application/vnd.flographit;audio/vnd.nuera.ecelp4800;audio/vnd.nuera.ecelp7470;audio/vnd.nuera.ecelp9600;application/oda;application/ogg;audio/ogg;video/ogg;application/vnd.oma.dd2+xml;applicatin/vnd.oasis.opendocument.text-web;application/oebps-package+xml;application/vnd.intu.qbo;application/vnd.openofficeorg.extension;application/vnd.yamaha.openscoreformat;audio/webm;video/webm;application/vnd.oasis.opendocument.char;application/vnd.oasis.opendocument.chart-template;application/vnd.oasis.opendocument.database;application/vnd.oasis.opendocument.formula;application/vnd.oasis.opendocument.formula-template;application/vnd.oasis.opendocument.grapics;application/vnd.oasis.opendocument.graphics-template;application/vnd.oasis.opendocument.image;application/vnd.oasis.opendocument.image-template;application/vnd.oasis.opendocument.presentation;application/vnd.oasis.opendocumen.presentation-template;application/vnd.oasis.opendocument.spreadsheet;application/vnd.oasis.opendocument.spreadsheet-template;application/vnd.oasis.opendocument.text;application/vnd.oasis.opendocument.text-master;application/vnd.asis.opendocument.text-template;image/ktx;application/vnd.sun.xml.calc;application/vnd.sun.xml.calc.template;application/vnd.sun.xml.draw;application/vnd.sun.xml.draw.template;application/vnd.sun.xml.impress;application/vnd.sun.xl.impress.template;application/vnd.sun.xml.math;application/vnd.sun.xml.writer;application/vnd.sun.xml.writer.global;application/vnd.sun.xml.writer.template;application/x-font-otf;application/vnd.yamaha.openscoreformat.osfpvg+xml;application/vnd.osgi.dp;application/vnd.palm;text/x-pascal;application/vnd.pawaafile;application/vnd.hp-pclxl;application/vnd.picsel;image/x-pcx;image/vnd.adobe.photoshop;application/pics-rules;image/x-pict;application/x-chat;aplication/pkcs10;application/x-pkcs12;application/pkcs7-mime;application/pkcs7-signature;application/x-pkcs7-certreqresp;application/x-pkcs7-certificates;application/pkcs8;application/vnd.pocketlearn;image/x-portable-anymap;image/-portable-bitmap;application/x-font-pcf;application/font-tdpfr;application/x-chess-pgn;image/x-portable-graymap;image/png;image/x-portable-pixmap;application/pskc+xml;application/vnd.ctc-posml;application/postscript;application/xfont-type1;application/vnd.powerbuilder6;application/pgp-encrypted;application/pgp-signature;application/vnd.previewsystems.box;application/vnd.pvi.ptid1;application/pls+xml;application/vnd.pg.format;application/vnd.pg.osasli;tex/prs.lines.tag;application/x-font-linux-psf;application/vnd.publishare-delta-tree;application/vnd.pmi.widget;application/vnd.quark.quarkxpress;application/vnd.epson.esf;application/vnd.epson.msf;application/vnd.epson.ssf;applicaton/vnd.epson.quickanime;application/vnd.intu.qfx;video/quicktime;application/x-rar-compressed;audio/x-pn-realaudio;audio/x-pn-realaudio-plugin;application/rsd+xml;application/vnd.rn-realmedia;application/vnd.realvnc.bed;applicatin/vnd.recordare.musicxml;application/vnd.recordare.musicxml+xml;application/relax-ng-compact-syntax;application/vnd.data-vision.rdz;application/rdf+xml;application/vnd.cloanto.rp9;application/vnd.jisp;application/rtf;text/richtex;application/vnd.route66.link66+xml;application/rss+xml;application/shf+xml;application/vnd.sailingtracker.track;image/svg+xml;application/vnd.sus-calendar;application/sru+xml;application/set-payment-initiation;application/set-reistration-initiation;application/vnd.sema;application/vnd.semd;application/vnd.semf;application/vnd.seemail;application/x-font-snf;application/scvp-vp-request;application/scvp-vp-response;application/scvp-cv-request;application/svp-cv-response;application/sdp;text/x-setext;video/x-sgi-movie;application/vnd.shana.informed.formdata;application/vnd.shana.informed.formtemplate;application/vnd.shana.informed.interchange;application/vnd.shana.informed.package;application/thraud+xml;application/x-shar;image/x-rgb;application/vnd.epson.salt;application/vnd.accpac.simply.aso;application/vnd.accpac.simply.imp;application/vnd.simtech-mindmapper;application/vnd.commonspace;application/vnd.ymaha.smaf-audio;application/vnd.smaf;application/vnd.yamaha.smaf-phrase;application/vnd.smart.teacher;application/vnd.svd;application/sparql-query;application/sparql-results+xml;application/srgs;application/srgs+xml;application/sml+xml;application/vnd.koan;text/sgml;application/vnd.stardivision.calc;application/vnd.stardivision.draw;application/vnd.stardivision.impress;application/vnd.stardivision.math;application/vnd.stardivision.writer;application/vnd.tardivision.writer-global;application/vnd.stepmania.stepchart;application/x-stuffit;application/x-stuffitx;application/vnd.solent.sdkm+xml;application/vnd.olpc-sugar;audio/basic;application/vnd.wqd;application/vnd.symbian.install;application/smil+xml;application/vnd.syncml+xml;application/vnd.syncml.dm+wbxml;application/vnd.syncml.dm+xml;application/x-sv4cpio;application/x-sv4crc;application/sbml+xml;text/tab-separated-values;image/tiff;application/vnd.to.intent-module-archive;application/x-tar;application/x-tcl;application/x-tex;application/x-tex-tfm;application/tei+xml;text/plain;application/vnd.spotfire.dxp;application/vnd.spotfire.sfs;application/timestamped-data;applicationvnd.trid.tpt;application/vnd.triscape.mxs;text/troff;application/vnd.trueapp;application/x-font-ttf;text/turtle;application/vnd.umajin;application/vnd.uoml+xml;application/vnd.unity;application/vnd.ufdl;text/uri-list;application/nd.uiq.theme;application/x-ustar;text/x-uuencode;text/x-vcalendar;text/x-vcard;application/x-cdlink;application/vnd.vsf;model/vrml;application/vnd.vcx;model/vnd.mts;model/vnd.vtu;application/vnd.visionary;video/vnd.vivo;applicatin/ccxml+xml,;application/voicexml+xml;application/x-wais-source;application/vnd.wap.wbxml;image/vnd.wap.wbmp;audio/x-wav;application/davmount+xml;application/x-font-woff;application/wspolicy+xml;image/webp;application/vnd.webturb;application/widget;application/winhlp;text/vnd.wap.wml;text/vnd.wap.wmlscript;application/vnd.wap.wmlscriptc;application/vnd.wordperfect;application/vnd.wt.stf;application/wsdl+xml;image/x-xbitmap;image/x-xpixmap;image/x-xwindowump;application/x-x509-ca-cert;application/x-xfig;application/xhtml+xml;application/xml;application/xcap-diff+xml;application/xenc+xml;application/patch-ops-error+xml;application/resource-lists+xml;application/rls-services+xml;aplication/resource-lists-diff+xml;application/xslt+xml;application/xop+xml;application/x-xpinstall;application/xspf+xml;application/vnd.mozilla.xul+xml;chemical/x-xyz;text/yaml;application/yang;application/yin+xml;application/vnd.ul;application/zip;application/vnd.handheld-entertainment+xml;application/vnd.zzazz.deck+xml");
                ffOptions.setProfile(ffProfile);
                ffOptions.setCapability(CapabilityType.PLATFORM_NAME, getDesiredOperatingSystem());
                ffOptions.setHeadless(HEADLESS_EXECUTION);
                ffOptions.addArguments("-foreground");
                ffOptions.setPageLoadStrategy(PageLoadStrategy.NORMAL);
                ffOptions.setPageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
                ffOptions.setScriptTimeout(Duration.ofSeconds(SCRIPT_TIMEOUT));
                if (Boolean.TRUE.equals(WAIT_IMPLICITLY)) {
                    ffOptions.setImplicitWaitTimeout(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT));
                }
                //Add Proxy Setting if found
                if (!PROXY_SERVER_SETTINGS.equals("")) {
                    Proxy proxy = new Proxy();
                    proxy.setHttpProxy(PROXY_SERVER_SETTINGS);
                    proxy.setFtpProxy(PROXY_SERVER_SETTINGS);
                    ffOptions.setProxy(proxy);
                }
            }
            case DESKTOP_INTERNET_EXPLORER -> {
                ieOptions = new InternetExplorerOptions();
                if (customDriverOptions != null) {
                    ieOptions = ieOptions.merge(customDriverOptions);
                }
                ieOptions.setCapability(CapabilityType.PLATFORM_NAME, getDesiredOperatingSystem());
                ieOptions.setPageLoadStrategy(PageLoadStrategy.NORMAL);
                ieOptions.setPageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
                ieOptions.setScriptTimeout(Duration.ofSeconds(SCRIPT_TIMEOUT));
                if (Boolean.TRUE.equals(WAIT_IMPLICITLY)) {
                    ieOptions.setImplicitWaitTimeout(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT));
                }
                //Add Proxy Setting if found
                if (!PROXY_SERVER_SETTINGS.equals("")) {
                    Proxy proxy = new Proxy();
                    proxy.setHttpProxy(PROXY_SERVER_SETTINGS);
                    proxy.setFtpProxy(PROXY_SERVER_SETTINGS);
                    ieOptions.setProxy(proxy);
                }
            }
            case APPIUM_CHROME, DESKTOP_CHROME, DESKTOP_EDGE -> {
                ChromiumOptions options;
                if (driverType.equals(DriverType.DESKTOP_EDGE)) {
                    options = new EdgeOptions();
                } else {
                    options = new ChromeOptions();
                }
                if (customDriverOptions != null) {
                    options = (ChromiumOptions) options.merge(customDriverOptions);
                }
                options.setCapability(CapabilityType.PLATFORM_NAME, getDesiredOperatingSystem());
                options.setHeadless(HEADLESS_EXECUTION);
                if (Boolean.TRUE.equals(AUTO_MAXIMIZE) && !isMobileWebExecution() && !OperatingSystemType.MACOS.equals(getOperatingSystemFromName(targetOperatingSystem))) {
                    options.addArguments("--start-maximized");
                }
                Map<String, Object> chromePreferences = new HashMap<>();
                chromePreferences.put("profile.default_content_settings.popups", 0);
                chromePreferences.put("download.prompt_for_download", "false");
                chromePreferences.put("download.default_directory", downloadsFolderPath);
                options.setExperimentalOption("prefs", chromePreferences);
                options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.ACCEPT_AND_NOTIFY);
                options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
                options.setPageLoadStrategy(PageLoadStrategy.NORMAL); // https://www.skptricks.com/2018/08/timed-out-receiving-message-from-renderer-selenium.html
                options.setPageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
                options.setScriptTimeout(Duration.ofSeconds(SCRIPT_TIMEOUT));
                if (Boolean.TRUE.equals(WAIT_IMPLICITLY)) {
                    options.setImplicitWaitTimeout(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT));
                }
                //Add Proxy Setting if found
                if (!PROXY_SERVER_SETTINGS.equals("")) {
                    Proxy proxy = new Proxy();
                    proxy.setHttpProxy(PROXY_SERVER_SETTINGS);
                    proxy.setFtpProxy(PROXY_SERVER_SETTINGS);
                    options.setProxy(proxy);
                }

                if (driverType.equals(DriverType.DESKTOP_EDGE)) {
                    edOptions = (EdgeOptions) options;
                } else {
                    chOptions = (ChromeOptions) options;
                }
            }
            case DESKTOP_SAFARI -> {
                sfOptions = new SafariOptions();
                if (customDriverOptions != null) {
                    sfOptions = sfOptions.merge(customDriverOptions);
                }
                sfOptions.setCapability(CapabilityType.PLATFORM_NAME, getDesiredOperatingSystem());
                sfOptions.setPageLoadStrategy(PageLoadStrategy.NORMAL);
                sfOptions.setPageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT));
                sfOptions.setScriptTimeout(Duration.ofSeconds(SCRIPT_TIMEOUT));
                if (Boolean.TRUE.equals(WAIT_IMPLICITLY)) {
                    sfOptions.setImplicitWaitTimeout(Duration.ofSeconds(IMPLICIT_WAIT_TIMEOUT));
                }
                //Add Proxy Setting if found
                if (!PROXY_SERVER_SETTINGS.equals("")) {
                    Proxy proxy = new Proxy();
                    proxy.setHttpProxy(PROXY_SERVER_SETTINGS);
                    proxy.setFtpProxy(PROXY_SERVER_SETTINGS);
                    sfOptions.setProxy(proxy);
                }
            }
            case APPIUM_MOBILE_NATIVE -> appiumCapabilities = new DesiredCapabilities(customDriverOptions);
            default -> failAction("Unsupported Driver Type \"" + driverName + "\".");
        }
    }

    private static void createNewLocalDriverInstanceForFirefox() {
        if (!"".equals(customDriverName) && !"".equals(customDriverPath)) {
            System.setProperty("webdriver.gecko.driver",
                    customDriverPath + customDriverName + setDriversExtecutableFileExtension());
        } else {
            ReportManager.logDiscrete(WEBDRIVERMANAGER_MESSAGE);
            WebDriverManager.firefoxdriver().setup();
        }
        driver.set(new FirefoxDriver(ffOptions));
        storeDriverInstance(DriverType.DESKTOP_FIREFOX.getValue());
        ReportManager.log("Successfully Opened Mozilla Firefox.");
    }

    private static void createNewLocalDriverInstanceForInternetExplorer() {
        if (!customDriverName.equals("") && !customDriverPath.equals("")) {
            System.setProperty("webdriver.ie.driver",
                    customDriverPath + customDriverName + setDriversExtecutableFileExtension());
        } else {
            ReportManager.logDiscrete(WEBDRIVERMANAGER_MESSAGE);
            WebDriverManager.iedriver().setup();
        }
        driver.set(new InternetExplorerDriver(ieOptions));
        storeDriverInstance(DriverType.DESKTOP_INTERNET_EXPLORER.getValue());
        ReportManager.log("Successfully Opened Microsoft Internet Explorer.");
    }

    private static void createNewLocalDriverInstanceForChrome() {
        if (!customDriverName.equals("") && !customDriverPath.equals("")) {
            System.setProperty("webdriver.chrome.driver",
                    customDriverPath + customDriverName + setDriversExtecutableFileExtension());
        } else {
            ReportManager.logDiscrete(WEBDRIVERMANAGER_MESSAGE);
            WebDriverManager.chromedriver().setup();
        }
        driver.set(new ChromeDriver(chOptions));
        storeDriverInstance(DriverType.DESKTOP_CHROME.getValue());
        ReportManager.log("Successfully Opened Google Chrome.");
    }

    private static void createNewLocalDriverInstanceForEdge() {
        if (!customDriverName.equals("") && !customDriverPath.equals("")) {
            System.setProperty("webdriver.edge.driver",
                    customDriverPath + customDriverName + setDriversExtecutableFileExtension());
        } else {
            ReportManager.logDiscrete(WEBDRIVERMANAGER_MESSAGE);
            WebDriverManager.edgedriver().setup();
        }
        driver.set(new EdgeDriver(edOptions));
        storeDriverInstance(DriverType.DESKTOP_EDGE.getValue());
        ReportManager.log("Successfully Opened Microsoft Edge.");
    }

    private static void createNewLocalDriverInstanceForSafari() {
        try {
            driver.set(new SafariDriver(sfOptions));
        } catch (SessionNotCreatedException e) {
            ReportManagerHelper.log(e);
            failAction("Failed to create a session on " + DriverType.DESKTOP_SAFARI);
        }
        storeDriverInstance(DriverType.DESKTOP_SAFARI.getValue());
        ReportManager.log("Successfully Opened Safari.");
    }

    private static WebDriver createNewLocalDriverInstance(String driverName) {
        String initialLog = "Attempting to run locally on: \"" + targetOperatingSystem + " | " + driverName + "\"";
        if (Boolean.TRUE.equals(HEADLESS_EXECUTION)) {
            initialLog = initialLog + ", Headless Execution";
        }
        ReportManager.log(initialLog + ".");
        var driverType = getDriverTypeFromName(driverName);

        try {
            switch (driverType) {
                case DESKTOP_FIREFOX -> createNewLocalDriverInstanceForFirefox();
                case DESKTOP_INTERNET_EXPLORER -> createNewLocalDriverInstanceForInternetExplorer();
                case DESKTOP_CHROME -> createNewLocalDriverInstanceForChrome();
                case DESKTOP_EDGE -> createNewLocalDriverInstanceForEdge();
                case DESKTOP_SAFARI -> createNewLocalDriverInstanceForSafari();
                default -> failAction("Unsupported Driver Type \"" + driverName + "\".");
            }
        } catch (SessionNotCreatedException exception) {
            failAction("Failed to create new Browser Session", exception);
        }
        return driver.get();
    }

    private static WebDriver createNewRemoteDriverInstance(String driverName) {
        DriverType driverType;

        if (isMobileNativeExecution()) {
            driverType = DriverType.APPIUM_MOBILE_NATIVE;
        } else {
            driverType = getDriverTypeFromName(driverName);
        }
        var initialLog = new StringBuilder();
        initialLog.append("Attempting to run remotely on: \"").append(targetOperatingSystem);

        if (!isMobileNativeExecution()) {
            initialLog.append(" | ").append(driverName);
        }

        initialLog.append(" | ").append(TARGET_HUB_URL).append("\"");

        if (Boolean.TRUE.equals(HEADLESS_EXECUTION) && !isMobileExecution()) {
            initialLog.append(", Headless Execution");
        }
        ReportManager.log(initialLog + ".");


        if (isMobileExecution()) {
            if (appiumCapabilities == null) {
                appiumCapabilities = setAppiumDesiredCapabilitiesList();
            } else {
                appiumCapabilities.merge(setAppiumDesiredCapabilitiesList());
            }
        }

        try {
            setValueToRemoteDriverInstance(driverName, driverType, appiumCapabilities);
        } catch (UnreachableBrowserException e) {
            killSwitch = true;
            failAction("Unreachable Browser, terminated test suite execution.", e);
        } catch (WebDriverException e) {
            ReportManagerHelper.log(e);
            if (e.getMessage().contains("Error forwarding the new session cannot find")) {
                ReportManager.log("Failed to run remotely on: \"" + targetOperatingSystem + "\", \"" + driverName + "\", \""
                        + TARGET_HUB_URL + "\".");
                failAction(
                        "Error forwarding the new session: Couldn't find a node that matches the desired capabilities.", e);
            } else {
                ReportManager.log("Failed to run remotely on: \"" + targetOperatingSystem + "\", \"" + driverName + "\", \""
                        + TARGET_HUB_URL + "\".");
                failAction("Unhandled Error.", e);
            }
        } catch (NoClassDefFoundError | MalformedURLException e) {
            failAction("Failed to create Remote WebDriver instance", e);
        }
        return driver.get();
    }

    private static void setValueToRemoteDriverInstance(String driverName, DriverType driverType, DesiredCapabilities mobileDesiredCapabilities) throws MalformedURLException {
        switch (driverType) {
            case DESKTOP_FIREFOX:
                ReportManager.logDiscrete(ffOptions.toString());
                driver.set(new RemoteWebDriver(new URL(TARGET_HUB_URL), ffOptions));
                break;
            case DESKTOP_INTERNET_EXPLORER:
                ReportManager.logDiscrete(ieOptions.toString());
                driver.set(new RemoteWebDriver(new URL(TARGET_HUB_URL), ieOptions));
                break;
            case DESKTOP_CHROME:
                ReportManager.logDiscrete(chOptions.toString());
                driver.set(new RemoteWebDriver(new URL(TARGET_HUB_URL), chOptions));
                break;
            case DESKTOP_EDGE:
                ReportManager.logDiscrete(edOptions.toString());
                driver.set(new RemoteWebDriver(new URL(TARGET_HUB_URL), edOptions));
                break;
            case DESKTOP_SAFARI:
                if (!isMobileExecution()) {
                    ReportManager.logDiscrete(sfOptions.toString());
                    driver.set(new RemoteWebDriver(new URL(TARGET_HUB_URL), sfOptions));
                } else {
                    ReportManager.logDiscrete(mobileDesiredCapabilities.toString());
                    driver.set(new AppiumDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                }
                break;
            case APPIUM_CHROME:
                ReportManager.logDiscrete(WEBDRIVERMANAGER_MESSAGE);
                WebDriverManager.chromedriver().browserVersion(System.getProperty("MobileBrowserVersion")).setup();
                mobileDesiredCapabilities.setCapability("chromedriverExecutable",
                        WebDriverManager.chromedriver().getDownloadedDriverPath());
//         mobileDesiredCapabilities.setCapability("appium:chromeOptions", Map.of("w3c", false));
                ReportManager.logDiscrete(mobileDesiredCapabilities.toString());
                driver.set(new AppiumDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                break;
            case APPIUM_CHROMIUM:
                WebDriverManager.chromedriver().browserVersion(System.getProperty("MobileBrowserVersion")).setup();
                mobileDesiredCapabilities.setCapability("chromedriverExecutable",
                        WebDriverManager.chromedriver().getDownloadedDriverPath());
                ReportManager.logDiscrete(mobileDesiredCapabilities.toString());
                driver.set(new AppiumDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                break;
            case APPIUM_BROWSER, APPIUM_MOBILE_NATIVE:
                ReportManager.logDiscrete(mobileDesiredCapabilities.toString());
                if ("Android".equals(targetOperatingSystem)) {
                    driver.set(new AndroidDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                } else if ("iOS".equals(targetOperatingSystem)) {
                    driver.set(new IOSDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                } else {
                    driver.set(new AppiumDriver(new URL(TARGET_HUB_URL), mobileDesiredCapabilities));
                    // will break in case of firefoxOS
                }
                break;
            default:
                failAction("Unsupported Driver Type \"" + driverName + "\".");
                break;
        }
        ReportManager.log("Successfully Opened \"" + driverType.getValue() + "\".");
        storeDriverInstance(driverName);
        ((RemoteWebDriver) driver.get()).setFileDetector(new LocalFileDetector());
    }

    private static Platform getDesiredOperatingSystem() {
        OperatingSystemType operatingSystem = getOperatingSystemFromName(targetOperatingSystem);

        switch (operatingSystem) {
            case WINDOWS:
                return Platform.WINDOWS;
            case LINUX:
                return Platform.LINUX;
            case MACOS:
                return Platform.MAC;
            case ANDROID:
                return Platform.ANDROID;
            case IOS:
                return Platform.IOS;
            default:
                ReportManager.log(
                        "Unsupported Operating System \"" + targetOperatingSystem + "\", setting target platform to [ANY].");
                return Platform.ANY;
        }
    }

    private static void attemptToCloseOrQuitDriver(WebDriver driver, boolean quit) {
        try {
            if (quit || isMobileNativeExecution()) {
                driver.quit();
            } else {
                driver.close();
            }
        } catch (WebDriverException e) {
            // driver was already closed
        } catch (Exception e) {
            ReportManagerHelper.log(e);
        }

    }

    private static void attachWebDriverLogs(WebDriver driver) {
            try {
                driver.manage().logs().getAvailableLogTypes().forEach(logType -> {
                            var logBuilder = new StringBuilder();
                            driver.manage().logs().get(logType).getAll().forEach(logEntry -> logBuilder.append(logEntry.toString()).append(System.lineSeparator()));
                            ReportManagerHelper.attach("Selenium WebDriver Logs", logType, logBuilder.toString());
                        }
                );
            } catch (WebDriverException e) {
                // exception when the defined logging is not supported
            }
    }

    private static DesiredCapabilities setAppiumDesiredCapabilitiesList() {
        var desiredCapabilities = new DesiredCapabilities();

        Map<String, String> caps = PropertyFileManager.getAppiumDesiredCapabilities();
        caps.forEach((capabilityName, value) -> {
            if (!value.trim().equals("")) {
                desiredCapabilities.setCapability(capabilityName.split("mobile_")[1], value);
            }
        });
        return desiredCapabilities;
    }

    /**
     * Create and/or return an instance of the target driver (maintains a single
     * instance per driver type) and checks for cross-compatibility between the
     * selected driver and operating system
     *
     * @param driverName the name of the driver that you want to run, currently
     *                   supports 'MozillaFirefox', 'MicrosoftInternetExplorer',
     *                   'GoogleChrome', and 'MicrosoftEdge'
     * @return a singleton driver instance
     */
    private static synchronized WebDriver getDriver(String driverName, MutableCapabilities customDriverOptions) {
        initializeSystemProperties();
        String internalDriverName = driverName;
        if (internalDriverName == null) {
            internalDriverName = TARGET_DRIVER_NAME;
        }

        if (isMobileWebExecution()) {
            internalDriverName = System.getProperty("mobile_browserName");
        }
        try {
            if (!isMobileNativeExecution()) {
                checkDriverOSCrossCompatibility(internalDriverName);
                // check cross-compatibility between the selected operating system and driver
                // and report in case they are not compatible
            }
            setDriverOptions(internalDriverName, customDriverOptions);
            // set driver options with respect to the target driver name
            if (Boolean.TRUE.equals(DRIVER_OBJECT_SINGLETON)) {
                closeAllDrivers();
            }

            if ("local".equals(EXECUTION_ADDRESS) && !isMobileExecution()) {
                // Manage local execution
                driver.set(createNewLocalDriverInstance(internalDriverName));
            } else {
                // Manage remote execution | or appium execution
                driver.set(createNewRemoteDriverInstance(internalDriverName));
            }

            if (Boolean.TRUE.equals(HEADLESS_EXECUTION)) {
                driver.get().manage().window().setSize(new Dimension(1920, 1080));
            }

            if (!isMobileNativeExecution()) {
                JavaScriptWaitManager.setDriver(driver.get());
                if (Boolean.TRUE.equals(AUTO_MAXIMIZE) && !isMobileWebExecution()
                        && OperatingSystemType.MACOS.equals(getOperatingSystemFromName(targetOperatingSystem))) {
                    BrowserActions.maximizeWindow(driver.get());
                }
            }
            // start session recording
            RecordManager.startVideoRecording(driver.get());
        } catch (NullPointerException e) {
            ReportManagerHelper.log(e);
            ReportManager.log("Unhandled Exception with Driver Type \"" + internalDriverName + "\".");
            Assert.fail("Unhandled Exception with Driver Type \"" + internalDriverName + "\".", e);
        }

        if (Boolean.parseBoolean(System.getProperty("heal-enabled").trim())) {
            ReportManager.logDiscrete("Initializing Healenium's Self Healing Driver...");
            driver.set(SelfHealingDriver.create(driver.get()));
        }
        return driver.get();
    }

    protected static void initializeSystemProperties() {
        AUTO_MAXIMIZE = Boolean
                .valueOf(System.getProperty("autoMaximizeBrowserWindow").trim());
        HEADLESS_EXECUTION = Boolean.valueOf(System.getProperty("headlessExecution").trim());
        EXECUTION_ADDRESS = System.getProperty("executionAddress").trim();
        // local OR hub ip:port
        TARGET_HUB_URL = "http://" + EXECUTION_ADDRESS + "/wd/hub";
        // Windows-64 | Linux-64 | Mac-64
        TARGET_DRIVER_NAME = System.getProperty("targetBrowserName");
        // Default | MozillaFirefox | MicrosoftInternetExplorer | GoogleChrome |
        // MicrosoftEdge | Safari
        TARGET_MOBILE_BROWSER_NAME = System.getProperty("mobile_browserName");
        PAGE_LOAD_TIMEOUT = Integer.parseInt(System.getProperty("pageLoadTimeout"));
        SCRIPT_TIMEOUT = Integer.parseInt(System.getProperty("scriptExecutionTimeout"));
        IMPLICIT_WAIT_TIMEOUT = Integer.parseInt(System.getProperty("implicitWaitTimeout"));
        WAIT_IMPLICITLY = Boolean.valueOf(System.getProperty("waitImplicitly").trim());
        DRIVER_OBJECT_SINGLETON = Boolean
                .valueOf(System.getProperty("browserObjectSingleton").trim());
        customDriverPath = System.getProperty("customDriverPath");
        customDriverName = System.getProperty("customDriverName");
        targetOperatingSystem = System.getProperty("targetOperatingSystem");
    }

    private static void storeDriverInstance(String driverName) {
        drivers.put(driverName + "_" + driver.get().hashCode(), new HashMap<>());
        drivers.get(driverName + "_" + driver.get().hashCode()).put(targetOperatingSystem, driver.get());
    }

    /**
     * Enum list of the supported operating systems for execution
     */
    private enum OperatingSystemType {
        LINUX("Linux-64"), MACOS("Mac-64"), WINDOWS("Windows-64"), ANDROID("Android"), IOS("iOS"),
        FIREFOXOS("FirefoxOS");

        private final String value;

        OperatingSystemType(String type) {
            this.value = type;
        }

        private String getValue() {
            return value;
        }
    }
}
